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

import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.impl.psi.filters.getters.InstanceOfLeftPartTypeGetter;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class InstanceofTypeProvider implements CompletionProvider {
  static final ElementPattern<PsiElement> AFTER_INSTANCEOF = psiElement().afterLeaf(PsiKeyword.INSTANCEOF);

  @RequiredReadAction
  @Override
  public void addCompletions(@Nonnull final CompletionParameters parameters, final ProcessingContext context, @Nonnull final CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final PsiType[] leftTypes = InstanceOfLeftPartTypeGetter.getLeftTypes(position);
    final Set<PsiClassType> expectedClassTypes = new LinkedHashSet<PsiClassType>();
    final Set<PsiClass> parameterizedTypes = new HashSet<PsiClass>();
    for (final PsiType type : leftTypes) {
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType) type;
        if (!classType.isRaw()) {
          ContainerUtil.addIfNotNull(parameterizedTypes, classType.resolve());
        }

        expectedClassTypes.add(classType.rawType());
      }
    }

    JavaInheritorsGetter.processInheritors(parameters, expectedClassTypes, result.getPrefixMatcher(), new Consumer<PsiType>() {
      @Override
      public void accept(PsiType type) {
        final PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass == null || psiClass instanceof PsiTypeParameter) {
          return;
        }

        //noinspection SuspiciousMethodCalls
        if (expectedClassTypes.contains(type)) {
          return;
        }

        result.addElement(createInstanceofLookupElement(psiClass, parameterizedTypes));
      }
    });
  }

  private static LookupElement createInstanceofLookupElement(PsiClass psiClass, Set<PsiClass> toWildcardInheritors) {
    final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
    if (typeParameters.length > 0) {
      for (final PsiClass parameterizedType : toWildcardInheritors) {
        if (psiClass.isInheritor(parameterizedType, true)) {
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          final PsiWildcardType wildcard = PsiWildcardType.createUnbounded(psiClass.getManager());
          for (final PsiTypeParameter typeParameter : typeParameters) {
            substitutor = substitutor.put(typeParameter, wildcard);
          }
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
          return PsiTypeLookupItem.createLookupItem(factory.createType(psiClass, substitutor), psiClass);
        }
      }
    }

    return new JavaPsiClassReferenceElement(psiClass);
  }
}
