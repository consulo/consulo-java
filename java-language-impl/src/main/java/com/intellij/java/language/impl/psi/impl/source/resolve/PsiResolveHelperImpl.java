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
package com.intellij.java.language.impl.psi.impl.source.resolve;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.PsiInferenceHelper;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.PsiGraphInferenceHelper;
import com.intellij.java.language.impl.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.java.language.impl.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.java.language.impl.psi.scope.processor.MethodResolverProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Singleton
@ServiceImpl
public class PsiResolveHelperImpl implements PsiResolveHelper {
  private static final Logger LOG = Logger.getInstance(PsiResolveHelperImpl.class);
  private final PsiManager myManager;

  @Inject
  public PsiResolveHelperImpl(PsiManager manager) {
    myManager = manager;
  }

  @Override
  @Nonnull
  public JavaResolveResult resolveConstructor(PsiClassType classType, @Nonnull PsiExpressionList argumentList, PsiElement place) {
    JavaResolveResult[] result = multiResolveConstructor(classType, argumentList, place);
    return result.length == 1 ? result[0] : JavaResolveResult.EMPTY;
  }

  @Override
  @Nonnull
  public JavaResolveResult[] multiResolveConstructor(@Nonnull PsiClassType type, @Nonnull PsiExpressionList argumentList, @Nonnull PsiElement place) {
    PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();
    PsiClass aClass = classResolveResult.getElement();
    if (aClass == null) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    final MethodResolverProcessor processor;
    PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
    if (argumentList.getParent() instanceof PsiAnonymousClass) {
      final PsiAnonymousClass anonymous = (PsiAnonymousClass) argumentList.getParent();
      processor = new MethodResolverProcessor(anonymous, argumentList, place, place.getContainingFile());
      aClass = anonymous.getBaseClassType().resolve();
      if (aClass == null) {
        return JavaResolveResult.EMPTY_ARRAY;
      }
    } else {
      processor = new MethodResolverProcessor(aClass, argumentList, place, place.getContainingFile());
    }

    ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor);
    for (PsiMethod constructor : aClass.getConstructors()) {
      if (!processor.execute(constructor, state)) {
        break;
      }
    }

    return processor.getResult();
  }

  @Override
  public PsiClass resolveReferencedClass(@Nonnull final String referenceText, final PsiElement context) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(myManager.getProject()).getParserFacade();
    try {
      final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(referenceText, context);
      PsiFile containingFile = ref.getContainingFile();
      LOG.assertTrue(containingFile.isValid(), referenceText);
      return ResolveClassUtil.resolveClass(ref, containingFile);
    } catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public PsiVariable resolveReferencedVariable(@Nonnull String referenceText, PsiElement context) {
    return resolveVar(referenceText, context, null);
  }

  @Override
  public PsiVariable resolveAccessibleReferencedVariable(@Nonnull String referenceText, PsiElement context) {
    final boolean[] problemWithAccess = new boolean[1];
    PsiVariable variable = resolveVar(referenceText, context, problemWithAccess);
    return problemWithAccess[0] ? null : variable;
  }

  @Nullable
  private PsiVariable resolveVar(@Nonnull String referenceText, final PsiElement context, final boolean[] problemWithAccess) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(myManager.getProject()).getParserFacade();
    try {
      final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(referenceText, context);
      return ResolveVariableUtil.resolveVariable(ref, problemWithAccess, null);
    } catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public boolean isAccessible(@Nonnull PsiMember member, @Nonnull PsiElement place, @Nullable PsiClass accessObjectClass) {
    return isAccessible(member, member.getModifierList(), place, accessObjectClass, null);
  }

  @Override
  public boolean isAccessible(@Nonnull PsiMember member,
                              @Nullable PsiModifierList modifierList,
                              @Nonnull PsiElement place,
                              @Nullable PsiClass accessObjectClass,
                              @Nullable PsiElement currentFileResolveScope) {
    PsiClass containingClass = member.getContainingClass();
    return JavaResolveUtil.isAccessible(member, containingClass, modifierList, place, accessObjectClass, currentFileResolveScope);
  }

  @Nonnull
  @Override
  public CandidateInfo[] getReferencedMethodCandidates(@Nonnull PsiCallExpression expr, boolean dummyImplicitConstructor, final boolean checkVarargs) {
    PsiFile containingFile = expr.getContainingFile();
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(expr, containingFile) {
      @Override
      protected boolean acceptVarargs() {
        return checkVarargs;
      }
    };
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, expr, dummyImplicitConstructor);
    } catch (MethodProcessorSetupFailedException e) {
      return CandidateInfo.EMPTY_ARRAY;
    }
    return processor.getCandidates();
  }

  @Nonnull
  @Override
  public CandidateInfo[] getReferencedMethodCandidates(@Nonnull PsiCallExpression call, boolean dummyImplicitConstructor) {
    return getReferencedMethodCandidates(call, dummyImplicitConstructor, false);
  }

  @Override
  public PsiType inferTypeForMethodTypeParameter(@Nonnull PsiTypeParameter typeParameter,
                                                 @Nonnull PsiParameter[] parameters,
                                                 @Nonnull PsiExpression[] arguments,
                                                 @Nonnull PsiSubstitutor partialSubstitutor,
                                                 @Nullable PsiElement parent,
                                                 @Nonnull ParameterTypeInferencePolicy policy) {
    return getInferenceHelper(PsiUtil.getLanguageLevel(parent != null ? parent : typeParameter)).inferTypeForMethodTypeParameter(typeParameter, parameters, arguments, partialSubstitutor, parent,
        policy);
  }

  @Override
  @Nonnull
  public PsiSubstitutor inferTypeArguments(@Nonnull PsiTypeParameter[] typeParameters,
                                           @Nonnull PsiParameter[] parameters,
                                           @Nonnull PsiExpression[] arguments,
                                           @Nonnull PsiSubstitutor partialSubstitutor,
                                           @Nonnull PsiElement parent,
                                           @Nonnull ParameterTypeInferencePolicy policy) {
    return getInferenceHelper(PsiUtil.getLanguageLevel(parent)).inferTypeArguments(typeParameters, parameters, arguments, partialSubstitutor, parent, policy, PsiUtil.getLanguageLevel(parent));
  }

  @Override
  @Nonnull
  public PsiSubstitutor inferTypeArguments(@Nonnull PsiTypeParameter[] typeParameters,
                                           @Nonnull PsiParameter[] parameters,
                                           @Nonnull PsiExpression[] arguments,
                                           @Nonnull PsiSubstitutor partialSubstitutor,
                                           @Nonnull PsiElement parent,
                                           @Nonnull ParameterTypeInferencePolicy policy,
                                           @Nonnull LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel).inferTypeArguments(typeParameters, parameters, arguments, partialSubstitutor, parent, policy, languageLevel);
  }

  @Override
  @Nonnull
  public PsiSubstitutor inferTypeArguments(@Nonnull PsiTypeParameter[] typeParameters, @Nonnull PsiType[] leftTypes, @Nonnull PsiType[] rightTypes, @Nonnull LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel).inferTypeArguments(typeParameters, leftTypes, rightTypes, languageLevel);
  }

  @Override
  public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam, PsiType param, PsiType arg, boolean isContraVariantPosition, LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel).getSubstitutionForTypeParameter(typeParam, param, arg, isContraVariantPosition, languageLevel);
  }

  @Nonnull
  public PsiInferenceHelper getInferenceHelper(@Nonnull LanguageLevel languageLevel) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      return new PsiGraphInferenceHelper(myManager);
    }
    return new PsiOldInferenceHelper(myManager);
  }
}
