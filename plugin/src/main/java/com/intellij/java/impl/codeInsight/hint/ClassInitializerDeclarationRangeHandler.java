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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.hint.DeclarationRangeHandler;
import com.intellij.java.language.psi.PsiClassInitializer;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ClassInitializerDeclarationRangeHandler implements DeclarationRangeHandler {
  @Nonnull
  @Override
  public Class getElementClass() {
    return PsiClassInitializer.class;
  }

  @Override
  @Nonnull
  public TextRange getDeclarationRange(@Nonnull final PsiElement container) {
    PsiClassInitializer initializer = (PsiClassInitializer) container;
    int startOffset = initializer.getModifierList().getTextRange().getStartOffset();
    int endOffset = initializer.getBody().getTextRange().getStartOffset();
    return new TextRange(startOffset, endOffset);
  }
}
