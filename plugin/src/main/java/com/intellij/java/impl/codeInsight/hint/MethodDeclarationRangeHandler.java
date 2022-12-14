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
package com.intellij.java.impl.codeInsight.hint;

import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.hint.DeclarationRangeHandler;
import consulo.language.psi.PsiElement;

import javax.annotation.Nonnull;

@ExtensionImpl
public class MethodDeclarationRangeHandler implements DeclarationRangeHandler {
  @Nonnull
  @Override
  public Class getElementClass() {
    return PsiMethod.class;
  }

  @Override
  @Nonnull
  public TextRange getDeclarationRange(@Nonnull final PsiElement container) {
    PsiMethod method = (PsiMethod) container;
    final TextRange textRange = method.getModifierList().getTextRange();
    int startOffset = textRange != null ? textRange.getStartOffset() : method.getTextOffset();
    int endOffset = method.getThrowsList().getTextRange().getEndOffset();
    return new TextRange(startOffset, endOffset);
  }
}
