/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.resolve;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.scope.PsiConflictResolver;
import com.intellij.java.language.impl.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.java.language.impl.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.java.language.impl.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.java.language.impl.psi.scope.processor.MethodResolverProcessor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author peter
 */
public class JavaMethodResolveHelper {
  private final Set<MethodSignature> myDuplicates = new HashSet<MethodSignature>();

  private final MethodCandidatesProcessor myProcessor;
  @Nullable
  private final PsiType[] myArgumentTypes;

  public JavaMethodResolveHelper(@Nonnull final PsiElement argumentList,
                                 PsiFile containingFile,
                                 @Nullable final PsiType[] argumentTypes) {
    myArgumentTypes = argumentTypes;
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(argumentList);
    final PsiConflictResolver resolver = argumentTypes == null ? DuplicateConflictResolver.INSTANCE : new
        JavaMethodsConflictResolver(argumentList, argumentTypes, languageLevel);
    myProcessor = new MethodResolverProcessor(argumentList, containingFile, new PsiConflictResolver[]{resolver}) {
      @Override
      protected MethodCandidateInfo createCandidateInfo(@Nonnull final PsiMethod method,
                                                        @Nonnull final PsiSubstitutor substitutor,
                                                        final boolean staticProblem,
                                                        final boolean accessible,
                                                        final boolean varargs) {
        return JavaMethodResolveHelper.this.createCandidateInfo(method, substitutor, staticProblem,
            myCurrentFileContext, !accessible, argumentList, argumentTypes, languageLevel);
      }

      @Override
      protected boolean isAccepted(final PsiMethod candidate) {
        return !candidate.isConstructor();
      }
    };
  }

  protected MethodCandidateInfo createCandidateInfo(@Nonnull PsiMethod method,
                                                    PsiSubstitutor substitutor,
                                                    boolean staticProblem,
                                                    PsiElement currentFileContext,
                                                    boolean accessProblem,
                                                    PsiElement argumentList,
                                                    PsiType[] argumentTypes,
                                                    @Nonnull LanguageLevel languageLevel) {
    return new MethodCandidateInfo(method, substitutor, accessProblem, staticProblem, argumentList,
        currentFileContext, argumentTypes, PsiType.EMPTY_ARRAY, languageLevel);
  }

  public void addMethod(@Nonnull PsiMethod method, @Nonnull PsiSubstitutor substitutor, boolean staticError) {
    if (myDuplicates.add(method.getSignature(substitutor))) {
      myProcessor.addMethod(method, substitutor, staticError);
    }
  }

  @Nonnull
  public ErrorType getResolveError() {
    final List<CandidateInfo> candidates = getCandidates();
    if (candidates.size() != 1) {
      return ErrorType.RESOLVE;
    }

    if (!candidates.get(0).isStaticsScopeCorrect()) {
      return ErrorType.STATIC;
    }

    return getResolveError((MethodCandidateInfo) candidates.get(0));
  }

  protected List<CandidateInfo> getCandidates() {
    return Arrays.asList(myProcessor.getCandidates());
  }

  protected ErrorType getResolveError(MethodCandidateInfo info) {
    if (myArgumentTypes == null) {
      return ErrorType.NONE;
    }

    if (!info.isApplicable()) {
      boolean hasNulls = false;
      //noinspection ConstantConditions
      final PsiParameter[] parameters = info.getElement().getParameterList().getParameters();
      if (myArgumentTypes.length == parameters.length) {
        for (int i = 0; i < myArgumentTypes.length; i++) {
          PsiType type = myArgumentTypes[i];
          if (type == null) {
            hasNulls = true;
          } else if (!parameters[i].getType().isAssignableFrom(type)) {
            return ErrorType.RESOLVE;
          }
        }
      }
      return hasNulls ? ErrorType.NONE : ErrorType.RESOLVE;
    }
    return ErrorType.NONE;
  }

  public void handleEvent(final PsiScopeProcessor.Event event, final Object associated) {
    myProcessor.handleEvent(event, associated);
  }

  public enum ErrorType {
    NONE, STATIC, RESOLVE
  }

  public Collection<JavaMethodCandidateInfo> getMethods() {
    return ContainerUtil.mapNotNull(getCandidates(), new Function<JavaResolveResult, JavaMethodCandidateInfo>() {
      @Override
      public JavaMethodCandidateInfo apply(final JavaResolveResult javaResolveResult) {
        return new JavaMethodCandidateInfo((PsiMethod) javaResolveResult.getElement(), javaResolveResult.getSubstitutor());
      }
    });
  }
}
