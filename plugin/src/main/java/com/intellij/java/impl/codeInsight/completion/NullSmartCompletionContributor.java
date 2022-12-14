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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiPrimitiveType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedHashSet;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiElement;
import static consulo.language.pattern.StandardPatterns.and;
import static consulo.language.pattern.StandardPatterns.not;

/**
 * @author peter
 */
@ExtensionImpl(id = "smartNull", order = "last, before javaSmart")
public class NullSmartCompletionContributor extends CompletionContributor {
  public NullSmartCompletionContributor() {
    extend(CompletionType.SMART, and(JavaSmartCompletionContributor.INSIDE_EXPRESSION, not(psiElement().afterLeaf("."))), new ExpectedTypeBasedCompletionProvider() {
      @Override
      protected void addCompletions(final CompletionParameters parameters, final CompletionResultSet result, final Collection<ExpectedTypeInfo> infos) {
        if (!StringUtil.startsWithChar(result.getPrefixMatcher().getPrefix(), 'n')) {
          return;
        }

        LinkedHashSet<CompletionResult> results = result.runRemainingContributors(parameters, true);
        for (CompletionResult completionResult : results) {
          if (completionResult.isStartMatch()) {
            return;
          }
        }

        for (final ExpectedTypeInfo info : infos) {
          if (!(info.getType() instanceof PsiPrimitiveType)) {
            final LookupElement item = BasicExpressionCompletionContributor.createKeywordLookupItem(parameters.getPosition(), PsiKeyword.NULL);
            result.addElement(JavaSmartCompletionContributor.decorate(item, infos));
            return;
          }
        }
      }
    });
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
