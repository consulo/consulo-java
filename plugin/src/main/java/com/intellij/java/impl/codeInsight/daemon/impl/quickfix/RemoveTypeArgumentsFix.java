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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.logging.Logger;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceParameterList;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiVariable;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: anna
 * Date: 1/18/12
 */
public class RemoveTypeArgumentsFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private static final Logger LOGGER = Logger.getInstance(RemoveTypeArgumentsFix.class);

  public RemoveTypeArgumentsFix(@jakarta.annotation.Nullable PsiElement element) {
    super(element);
  }

  @jakarta.annotation.Nonnull
  @Override
  public String getText() {
    return "Remove type arguments";
  }

  @jakarta.annotation.Nonnull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@jakarta.annotation.Nonnull Project project,
                             @Nonnull PsiFile file,
                             @jakarta.annotation.Nonnull PsiElement startElement,
                             @jakarta.annotation.Nonnull PsiElement endElement) {
    return startElement instanceof PsiVariable && startElement.isValid() && ((PsiVariable)startElement).getTypeElement() != null;
  }

  @Override
  public void invoke(@jakarta.annotation.Nonnull Project project,
                     @jakarta.annotation.Nonnull PsiFile file,
                     @Nullable Editor editor,
                     @jakarta.annotation.Nonnull PsiElement startElement,
                     @jakarta.annotation.Nonnull PsiElement endElement) {
    final PsiVariable psiVariable = (PsiVariable)startElement;
    final PsiTypeElement typeElement = psiVariable.getTypeElement();
    LOGGER.assertTrue(typeElement != null);
    final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
    if (referenceElement != null) {
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList != null) {
        parameterList.delete();
      }
    }
  }
}
