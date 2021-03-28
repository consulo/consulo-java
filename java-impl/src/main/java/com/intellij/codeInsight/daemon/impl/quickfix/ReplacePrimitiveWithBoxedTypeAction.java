/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiWildcardType;

/**
 * User: anna
 */
public class ReplacePrimitiveWithBoxedTypeAction extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myPrimitiveName;
  private final String myBoxedTypeName;
  private static final Logger LOG = Logger.getInstance(ReplacePrimitiveWithBoxedTypeAction.class);

  public ReplacePrimitiveWithBoxedTypeAction(@Nonnull PsiTypeElement element, @Nonnull String typeName, @Nonnull String boxedTypeName) {
    super(element);
    myPrimitiveName = typeName;
    myBoxedTypeName = boxedTypeName;
  }

  @Nonnull
  @Override
  public String getText() {
    return "Convert '" + myPrimitiveName + "' to '" + myBoxedTypeName + "'";
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project,
                             @Nonnull PsiFile file,
                             @Nonnull PsiElement startElement,
                             @Nonnull PsiElement endElement) {
    if (startElement instanceof PsiTypeElement) {
      PsiType type = ((PsiTypeElement)startElement).getType();
      if (type instanceof PsiWildcardType) {
        type = ((PsiWildcardType)type).getBound();
      }
      if (type instanceof PsiPrimitiveType) {
        return ((PsiPrimitiveType)type).getBoxedType(startElement) != null;
      }
    }
    return false;
  }

  @Override
  public void invoke(@Nonnull Project project,
                     @Nonnull PsiFile file,
                     @Nullable Editor editor,
                     @Nonnull PsiElement startElement,
                     @Nonnull PsiElement endElement) {
    final PsiType type = ((PsiTypeElement)startElement).getType();
    PsiType boxedType;
    if (type instanceof PsiPrimitiveType) {
      boxedType = ((PsiPrimitiveType)type).getBoxedType(startElement);
    } else {
      LOG.assertTrue(type instanceof PsiWildcardType);
      final PsiWildcardType wildcardType = (PsiWildcardType)type;
      final PsiClassType boxedBound = ((PsiPrimitiveType)wildcardType.getBound()).getBoxedType(startElement);
      LOG.assertTrue(boxedBound != null);
      boxedType = wildcardType.isExtends() ? PsiWildcardType.createExtends(startElement.getManager(), boxedBound)
                                           : PsiWildcardType.createSuper(startElement.getManager(), boxedBound);
    }
    LOG.assertTrue(boxedType != null);
    startElement.replace(JavaPsiFacade.getElementFactory(project).createTypeElement(boxedType));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
