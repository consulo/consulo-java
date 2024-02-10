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

import consulo.language.editor.completion.lookup.TailType;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.TailTypeDecorator;
import com.intellij.java.language.impl.psi.impl.source.PsiLabelReference;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.completion.CompletionProvider;
import jakarta.annotation.Nonnull;

import java.util.List;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class LabelReferenceCompletion implements CompletionProvider {
  static final ElementPattern<PsiElement> LABEL_REFERENCE = psiElement().afterLeaf(PsiKeyword.BREAK, PsiKeyword.CONTINUE);

  static List<LookupElement> processLabelReference(PsiLabelReference ref) {
    return ContainerUtil.map(ref.getVariants(), s -> TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.SEMICOLON));
  }

  @RequiredReadAction
  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result) {
    PsiReference ref = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
    if (ref instanceof PsiLabelReference) {
      result.addAllElements(processLabelReference((PsiLabelReference) ref));
    }
  }
}
