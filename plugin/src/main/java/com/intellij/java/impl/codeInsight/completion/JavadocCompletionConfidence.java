/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionConfidence;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.PsiJavaReference;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiMultiReference;
import consulo.util.lang.ThreeState;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
@ExtensionImpl(id = "javadoc", order = "before javaComments")
public class JavadocCompletionConfidence extends CompletionConfidence {

  @Nonnull
  @Override
  public ThreeState shouldSkipAutopopup(@jakarta.annotation.Nonnull PsiElement contextElement, @Nonnull PsiFile psiFile, int offset) {
    if (psiElement().inside(PsiDocTag.class).accepts(contextElement)) {
      if (findJavaReference(psiFile, offset - 1) != null) {
        return ThreeState.NO;
      }
      if (PlatformPatterns.psiElement(JavaDocTokenType.DOC_TAG_NAME).accepts(contextElement)) {
        return ThreeState.NO;
      }
      if (contextElement.textMatches("#")) {
        return ThreeState.NO;
      }
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }

  @Nullable
  private static PsiJavaReference findJavaReference(final PsiFile file, final int offset) {
    PsiReference reference = file.findReferenceAt(offset);
    if (reference instanceof PsiMultiReference) {
      for (final PsiReference psiReference : ((PsiMultiReference) reference).getReferences()) {
        if (psiReference instanceof PsiJavaReference) {
          return (PsiJavaReference) psiReference;
        }
      }
    }
    return reference instanceof PsiJavaReference ? (PsiJavaReference) reference : null;
  }

  @jakarta.annotation.Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
