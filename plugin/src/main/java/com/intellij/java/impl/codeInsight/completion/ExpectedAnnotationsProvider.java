/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import com.intellij.java.impl.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiNameValuePair;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.completion.CompletionProvider;
import jakarta.annotation.Nonnull;

import static consulo.language.pattern.StandardPatterns.or;

/**
 * @author peter
 */
class ExpectedAnnotationsProvider implements CompletionProvider {
  static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_VALUE = or(PsiJavaPatterns.psiElement().withParent(PsiNameValuePair.class), PsiJavaPatterns.psiElement().withSuperParent(2,
      PsiNameValuePair.class));

  @RequiredReadAction
  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result) {
    PsiElement element = parameters.getPosition();

    for (PsiType type : ExpectedTypesGetter.getExpectedTypes(element, false)) {
      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null && psiClass.isAnnotationType()) {
        result.addElement(AllClassesGetter.createLookupItem(psiClass, AnnotationInsertHandler.INSTANCE));
      }
    }
  }
}
