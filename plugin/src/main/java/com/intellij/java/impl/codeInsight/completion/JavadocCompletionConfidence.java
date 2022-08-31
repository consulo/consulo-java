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

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.PsiJavaReference;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.util.ThreeState;

import javax.annotation.Nonnull;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavadocCompletionConfidence extends CompletionConfidence {

  @Nonnull
  @Override
  public ThreeState shouldSkipAutopopup(@Nonnull PsiElement contextElement, @Nonnull PsiFile psiFile, int offset) {
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

  @javax.annotation.Nullable
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

}
