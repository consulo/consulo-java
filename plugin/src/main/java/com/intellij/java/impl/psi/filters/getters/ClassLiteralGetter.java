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
package com.intellij.java.impl.psi.filters.getters;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.completion.JavaSmartCompletionParameters;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.completion.AutoCompletionPolicy;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Consumer;

public class ClassLiteralGetter {

  public static void addCompletions(@Nonnull JavaSmartCompletionParameters parameters,
                                    @Nonnull Consumer<LookupElement> result, PrefixMatcher matcher) {
    PsiType expectedType = parameters.getExpectedType();
    if (!InheritanceUtil.isInheritor(expectedType, CommonClassNames.JAVA_LANG_CLASS)) {
      return;
    }

    PsiType classParameter = PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_LANG_CLASS, 0, false);

    boolean addInheritors = false;
    PsiElement position = parameters.getPosition();
    if (classParameter instanceof PsiWildcardType) {
      PsiWildcardType wildcardType = (PsiWildcardType)classParameter;
      classParameter = wildcardType.isSuper() ? wildcardType.getSuperBound() : wildcardType.getExtendsBound();
      addInheritors = wildcardType.isExtends() && classParameter instanceof PsiClassType;
    } else if (!matcher.getPrefix().isEmpty()) {
      addInheritors = true;
      classParameter = PsiType.getJavaLangObject(position.getManager(), position.getResolveScope());
    }
    if (classParameter != null) {
      PsiFile file = position.getContainingFile();
      addClassLiteralLookupElement(classParameter, result, file);
      if (addInheritors) {
        addInheritorClassLiterals(file, classParameter, result, matcher);
      }
    }
  }

  private static void addInheritorClassLiterals(PsiFile context,
                                                PsiType classParameter,
                                                Consumer<LookupElement> result, PrefixMatcher matcher) {
    String canonicalText = classParameter.getCanonicalText();
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(canonicalText) && StringUtil.isEmpty(matcher.getPrefix())) {
      return;
    }

    CodeInsightUtil.processSubTypes(classParameter, context, true, matcher, (Consumer<PsiType>) type -> addClassLiteralLookupElement(type, result, context));
  }

  private static void addClassLiteralLookupElement(@Nullable PsiType type, Consumer<LookupElement> resultSet, PsiFile context) {
    if (type instanceof PsiClassType &&
        PsiUtil.resolveClassInType(type) != null &&
        !((PsiClassType)type).hasParameters() &&
        !(((PsiClassType)type).resolve() instanceof PsiTypeParameter)) {
      try {
        resultSet.accept(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new ClassLiteralLookupElement((PsiClassType)type, context)));
      }
      catch (IncorrectOperationException ignored) {
      }
    }
  }
}
