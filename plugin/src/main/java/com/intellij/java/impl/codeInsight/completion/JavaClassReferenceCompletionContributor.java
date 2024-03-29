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

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.codeInsight.completion.LegacyCompletionContributor;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiMultiReference;
import consulo.language.psi.PsiReference;
import consulo.ui.ex.action.IdeActions;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author peter
 */
@ExtensionImpl(id = "javaClassReference", order = "before legacy")
public class JavaClassReferenceCompletionContributor extends CompletionContributor {
  @Override
  public void duringCompletion(@Nonnull CompletionInitializationContext context) {
    JavaClassReference reference = findJavaClassReference(context.getFile(), context.getStartOffset());
    if (reference != null && reference.getExtendClassNames() != null) {
      JavaClassReferenceSet set = reference.getJavaClassReferenceSet();
      context.setReplacementOffset(set.getRangeInElement().getEndOffset() + set.getElement().getTextRange().getStartOffset());
    }
  }

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    JavaClassReference reference = findJavaClassReference(position.getContainingFile(), parameters.getOffset());
    if (reference == null) {
      return;
    }

    String[] extendClassNames = reference.getExtendClassNames();
    PsiElement context = reference.getCompletionContext();
    if (extendClassNames != null && context instanceof PsiJavaPackage) {
      if (parameters.getCompletionType() == CompletionType.SMART) {
        JavaClassReferenceSet set = reference.getJavaClassReferenceSet();
        int setStart = set.getRangeInElement().getStartOffset() + set.getElement().getTextRange().getStartOffset();
        String fullPrefix = parameters.getPosition().getContainingFile().getText().substring(setStart, parameters.getOffset());
        reference.processSubclassVariants((PsiJavaPackage) context, extendClassNames, result.withPrefixMatcher(fullPrefix));
        return;
      }
      result.addLookupAdvertisement("Press " + getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION) + " to see inheritors of " +
          StringUtil.join(extendClassNames, ", "));
    }

    if (parameters.getCompletionType() == CompletionType.SMART) {
      return;
    }

    if (parameters.isExtendedCompletion() || parameters.getCompletionType() == CompletionType.CLASS_NAME) {
      JavaClassNameCompletionContributor.addAllClasses(parameters, result);
    } else {
      LegacyCompletionContributor.completeReference(parameters, result);
    }
    result.stopHere();
  }

  @Nullable
  public static JavaClassReference findJavaClassReference(final PsiFile file, final int offset) {
    PsiReference reference = file.findReferenceAt(offset);
    if (reference instanceof PsiMultiReference) {
      for (final PsiReference psiReference : ((PsiMultiReference) reference).getReferences()) {
        if (psiReference instanceof JavaClassReference) {
          return (JavaClassReference) psiReference;
        }
      }
    }
    return reference instanceof JavaClassReference ? (JavaClassReference) reference : null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return Language.ANY;
  }
}
