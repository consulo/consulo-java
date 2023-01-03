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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.xml.codeInsight.completion.XmlCompletionContributor;
import consulo.xml.lang.xml.XMLLanguage;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author peter
 */
@ExtensionImpl(id = "basic2ClassName", order = "after xml")
public class XmlBasicToClassNameDelegator extends CompletionContributor {

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiFile file = position.getContainingFile();
    if (parameters.getCompletionType() != CompletionType.BASIC ||
        !JavaCompletionContributor.mayStartClassName(result) ||
        !file.getLanguage().isKindOf(XMLLanguage.INSTANCE)) {
      return;
    }

    final boolean empty = result.runRemainingContributors(parameters, true).isEmpty();

    if (!empty && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (empty && JavaClassReferenceCompletionContributor.findJavaClassReference(file, parameters.getOffset()) != null ||
        parameters.isExtendedCompletion()) {
      CompletionService.getCompletionService().getVariantsFromContributors(parameters.delegateToClassName(), null, new Consumer<CompletionResult>() {
        @Override
        public void accept(final CompletionResult completionResult) {
          LookupElement lookupElement = completionResult.getLookupElement();
          JavaPsiClassReferenceElement classElement = lookupElement.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
          if (classElement != null) {
            classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          }
          lookupElement.putUserData(XmlCompletionContributor.WORD_COMPLETION_COMPATIBLE, Boolean.TRUE); //todo think of a less dirty interaction
          result.passResult(completionResult);
        }
      });
    }
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return XMLLanguage.INSTANCE;
  }
}
