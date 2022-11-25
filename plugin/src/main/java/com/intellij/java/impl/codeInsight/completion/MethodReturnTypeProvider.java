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

import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
class MethodReturnTypeProvider implements CompletionProvider {
  protected static final ElementPattern<PsiElement> IN_METHOD_RETURN_TYPE = psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class,
      PsiMethod.class).andNot(JavaKeywordCompletion.AFTER_DOT);

  @RequiredReadAction
  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull final CompletionResultSet result) {
    addProbableReturnTypes(parameters, result);
  }

  static void addProbableReturnTypes(@Nonnull CompletionParameters parameters, final Consumer<LookupElement> consumer) {
    final PsiElement position = parameters.getPosition();
    PsiMethod method = PsiTreeUtil.getParentOfType(position, PsiMethod.class);
    assert method != null;

    final PsiTypeVisitor<PsiType> eachProcessor = new PsiTypeVisitor<PsiType>() {
      private Set<PsiType> myProcessed = new HashSet<>();

      @Nullable
      @Override
      public PsiType visitType(PsiType type) {
        if (myProcessed.add(type)) {
          int priority = type.equalsToText(JavaClassNames.JAVA_LANG_OBJECT) ? 1 : 1000 - myProcessed.size();
          consumer.accept(PrioritizedLookupElement.withPriority(PsiTypeLookupItem.createLookupItem(type, position), priority));
        }
        return type;
      }
    };
    for (PsiType type : getReturnTypeCandidates(method)) {
      eachProcessor.visitType(type);
      ExpectedTypesProvider.processAllSuperTypes(type, eachProcessor, position.getProject(), new HashSet<>());
    }
  }

  private static PsiType[] getReturnTypeCandidates(@Nonnull PsiMethod method) {
    PsiType lub = null;
    boolean hasVoid = false;
    for (PsiReturnStatement statement : PsiUtil.findReturnStatements(method)) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) {
        hasVoid = true;
      } else {
        PsiType type = value.getType();
        if (lub == null) {
          lub = type;
        } else if (type != null) {
          lub = GenericsUtil.getLeastUpperBound(lub, type, method.getManager());
        }
      }
    }
    if (hasVoid && lub == null) {
      lub = PsiType.VOID;
    }
    if (lub instanceof PsiIntersectionType) {
      return ((PsiIntersectionType) lub).getConjuncts();
    }
    return lub == null ? PsiType.EMPTY_ARRAY : new PsiType[]{lub};
  }
}
