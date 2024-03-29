/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.navigation.actions;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.action.TypeDeclarationProvider;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.TargetElementUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaTypeDeclarationProvider extends TypeDeclarationProvider {
  @RequiredReadAction
  @Nullable
  @Override
  public PsiElement[] getSymbolTypeDeclarations(@Nonnull PsiElement targetElement, Editor editor, int offset) {
    PsiType type;
    if (targetElement instanceof PsiVariable) {
      type = ((PsiVariable) targetElement).getType();
    } else if (targetElement instanceof PsiMethod) {
      type = ((PsiMethod) targetElement).getReturnType();
    } else {
      return null;
    }
    if (type == null) return null;
    if (editor != null) {
      final PsiReference reference = TargetElementUtil.findReference(editor, offset);
      if (reference instanceof PsiJavaReference) {
        final JavaResolveResult resolveResult = ((PsiJavaReference) reference).advancedResolve(true);
        type = resolveResult.getSubstitutor().substitute(type);
      }
    }
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass == null ? null : new PsiElement[]{psiClass};
  }
}
