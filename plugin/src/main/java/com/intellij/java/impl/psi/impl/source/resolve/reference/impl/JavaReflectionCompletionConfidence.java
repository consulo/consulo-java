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
package com.intellij.java.impl.psi.impl.source.resolve.reference.impl;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionConfidence;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ThreeState;

import jakarta.annotation.Nonnull;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl(id = "javaReflection")
public class JavaReflectionCompletionConfidence extends CompletionConfidence {

  @Nonnull
  @Override
  public ThreeState shouldSkipAutopopup(@Nonnull PsiElement contextElement, @Nonnull PsiFile psiFile, int offset) {
    final PsiElement literal = contextElement.getParent();
    if (literal != null && (JavaReflectionReferenceContributor.PATTERN.accepts(literal) || JavaReflectionReferenceContributor.CLASS_PATTERN.accepts(literal))) {
      return ThreeState.NO;
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
