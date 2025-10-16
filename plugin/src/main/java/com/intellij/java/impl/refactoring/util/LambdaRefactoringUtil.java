/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.analysis.impl.codeInspection.RedundantLambdaCodeBlockInspection;
import com.intellij.java.impl.refactoring.introduceField.ElementToWorkOn;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.analysis.impl.codeInspection.SideEffectChecker;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.component.util.text.UniqueNameGenerator;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class LambdaRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(LambdaRefactoringUtil.class);

  @Nullable
  public static PsiExpression convertToMethodCallInLambdaBody(PsiMethodReferenceExpression element) {
    final PsiLambdaExpression lambdaExpression = convertMethodReferenceToLambda(element, false, true);
    return lambdaExpression != null ? LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody()) : null;
  }

  @Nullable
  public static PsiLambdaExpression convertMethodReferenceToLambda(final PsiMethodReferenceExpression referenceExpression, final boolean ignoreCast, final boolean simplifyToExpressionLambda) {
    PsiLambdaExpression lambdaExpression = createLambda(referenceExpression, ignoreCast);
    if (lambdaExpression == null) {
      return null;
    }
    lambdaExpression = (PsiLambdaExpression) referenceExpression.replace(lambdaExpression);

    if (simplifyToExpressionLambda) {
      simplifyToExpressionLambda(lambdaExpression);
    }

    return lambdaExpression;
  }

  public static boolean canConvertToLambda(PsiMethodReferenceExpression referenceExpression) {
    return createLambda(referenceExpression, false) != null;
  }

  /**
   * Convert method reference to lambda if possible and return the created lambda without replacing original method reference.
   *
   * @param referenceExpression    a method reference to convert
   * @param doNotAddParameterTypes if false, parameter types could be added to the lambda to resolve ambiguity
   * @return a created lambda or null if conversion fails
   */
  public static PsiLambdaExpression createLambda(PsiMethodReferenceExpression referenceExpression, boolean doNotAddParameterTypes) {
    String lambda = createLambdaWithoutFormalParameters(referenceExpression);
    if (lambda == null) {
      return null;
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(referenceExpression.getProject());
    PsiLambdaExpression lambdaExpression = (PsiLambdaExpression) elementFactory.createExpressionFromText(lambda, referenceExpression);
    final PsiType functionalInterfaceType = referenceExpression.getFunctionalInterfaceType();
    boolean needToSpecifyFormalTypes = !doNotAddParameterTypes && !isInferredSameTypeAfterConversion(lambdaExpression, referenceExpression, functionalInterfaceType);
    if (needToSpecifyFormalTypes) {
      PsiParameterList typedParamList = specifyLambdaParameterTypes(functionalInterfaceType, lambdaExpression);
      if (typedParamList == null) {
        return null;
      }
    }
    return lambdaExpression;
  }

  @RequiredReadAction
  private static String createLambdaWithoutFormalParameters(PsiMethodReferenceExpression referenceExpression) {
    PsiType functionalInterfaceType = referenceExpression.getFunctionalInterfaceType();
    final PsiElement resolve = referenceExpression.resolve();
    if (resolve == null) {
      return null;
    }
    final PsiClassType.ClassResolveResult functionalInterfaceResolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (interfaceMethod == null) {
      return null;
    }
    final PsiSubstitutor psiSubstitutor = LambdaUtil.getSubstitutor(interfaceMethod, functionalInterfaceResolveResult);
    final MethodSignature signature = interfaceMethod.getSignature(psiSubstitutor);
    final boolean isReceiver = resolve instanceof PsiMethod method && PsiMethodReferenceUtil.isResolvedBySecondSearch(
      referenceExpression,
      signature,
      method.isVarArgs(),
      method.hasModifierProperty(PsiModifier.STATIC),
      method.getParameterList().getParametersCount()
    );
    final PsiParameter[] psiParameters = resolve instanceof PsiMethod method ? method.getParameterList().getParameters() : null;

    final PsiParameterList parameterList = interfaceMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();

    final Map<PsiParameter, String> map = new HashMap<>();
    final UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(referenceExpression.getProject());
    Function<PsiParameter, String> paramPresentationFunction = parameter ->
    {
      final int parameterIndex = parameterList.getParameterIndex(parameter);
      String baseName;
      if (isReceiver && parameterIndex == 0) {
        final SuggestedNameInfo nameInfo =
          codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, psiSubstitutor.substitute(parameter.getType()));
        baseName = nameInfo.names.length > 0 ? nameInfo.names[0] : parameter.getName();
      } else {
        String initialName;
        if (psiParameters != null) {
          final int idx = parameterIndex - (isReceiver ? 1 : 0);
          initialName = psiParameters.length > 0
            ? psiParameters[idx < psiParameters.length ? idx : psiParameters.length - 1].getName()
            : parameter.getName();
        } else {
          initialName = parameter.getName();
        }
        LOG.assertTrue(initialName != null);
        if ("_".equals(initialName)) {
          SuggestedNameInfo nameInfo =
            codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, psiSubstitutor.substitute(parameter.getType()));
          if (nameInfo.names.length > 0) {
            initialName = nameInfo.names[0];
          }
        }
        baseName = codeStyleManager.variableNameToPropertyName(initialName, VariableKind.PARAMETER);
      }

      if (baseName != null) {
        String parameterName =
          nameGenerator.generateUniqueName(codeStyleManager.suggestUniqueVariableName(baseName, referenceExpression, true));
        map.put(parameter, parameterName);
        return parameterName;
      }
      return "";
    };
    StringBuilder buf = new StringBuilder();
    if (parameters.length == 1) {
      buf.append(paramPresentationFunction.apply(parameters[0]));
    } else {
      buf.append("(").append(StringUtil.join(parameters, paramPresentationFunction, ", ")).append(")");
    }
    buf.append(" -> ");

    final JavaResolveResult resolveResult = referenceExpression.advancedResolve(false);
    final PsiElement resolveElement = resolveResult.getElement();

    if (resolveElement instanceof PsiMember) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(referenceExpression.getProject());
      buf.append("{");

      if (!PsiType.VOID.equals(interfaceMethod.getReturnType())) {
        buf.append("return ");
      }
      final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult =
        PsiMethodReferenceUtil.getQualifierResolveResult(referenceExpression);
      final PsiElement qualifier = referenceExpression.getQualifier();
      PsiClass containingClass = qualifierResolveResult.getContainingClass();

      final boolean onArrayRef = elementFactory.getArrayClass(PsiUtil.getLanguageLevel(referenceExpression)) == containingClass;

      final PsiElement referenceNameElement = referenceExpression.getReferenceNameElement();
      if (isReceiver) {
        buf.append(map.get(parameters[0])).append(".");
      } else {
        if (!(referenceNameElement instanceof PsiKeyword)) {
          if (qualifier instanceof PsiTypeElement typeElement) {
            final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
            LOG.assertTrue(referenceElement != null);
            if (!PsiTreeUtil.isAncestor(containingClass, referenceExpression, false)) {
              buf.append(referenceElement.getReferenceName()).append(".");
            }
          } else if (qualifier != null && !isQualifierUnnecessary(qualifier, containingClass)) {
            buf.append(qualifier.getText()).append(".");
          }
        }
      }

      //new or method name
      buf.append(referenceExpression.getReferenceName());

      if (referenceNameElement instanceof PsiKeyword) {
        //class name
        buf.append(" ");
        if (onArrayRef) {
          if (qualifier instanceof PsiTypeElement typeElement) {
            final PsiType type = typeElement.getType();
            int dim = type.getArrayDimensions();
            buf.append(type.getDeepComponentType().getCanonicalText());
            buf.append("[").append(map.get(parameters[0])).append("]");
            while (--dim > 0) {
              buf.append("[]");
            }
          }
        } else {
          buf.append(((PsiMember) resolveElement).getName());

          final PsiSubstitutor substitutor = resolveResult.getSubstitutor();

          LOG.assertTrue(containingClass != null);
          if (containingClass.hasTypeParameters() && !PsiUtil.isRawSubstitutor(containingClass, substitutor)) {
            buf.append("<").append(StringUtil.join(
              containingClass.getTypeParameters(),
              parameter ->
              {
                final PsiType psiType = substitutor.substitute(parameter);
                LOG.assertTrue(psiType != null);
                return psiType.getCanonicalText();
              },
              ", "
            )).append(">");
          }
        }
      }

      if (!onArrayRef || isReceiver) {
        //param list
        buf.append("(");
        boolean first = true;
        for (int i = isReceiver ? 1 : 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          if (!first) {
            buf.append(", ");
          } else {
            first = false;
          }
          buf.append(map.get(parameter));
        }
        buf.append(")");
      }

      buf.append(";}");
    }
    return buf.toString();
  }

  @RequiredReadAction
  private static boolean isQualifierUnnecessary(PsiElement qualifier, PsiClass containingClass) {
    if (qualifier instanceof PsiReferenceExpression reference) {
      if (reference.resolve() instanceof PsiClass && reference.getQualifier() == null
        && PsiTreeUtil.isContextAncestor(containingClass, qualifier, false)) {
        return true;
      }
    }
    return qualifier instanceof PsiThisExpression thisExpression && thisExpression.getQualifier() == null;
  }

  private static boolean isInferredSameTypeAfterConversion(
    PsiLambdaExpression lambdaExpression,
    PsiMethodReferenceExpression methodReferenceExpression,
    PsiType functionalInterfaceType
  ) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodReferenceExpression.getParent());
    if (!(parent instanceof PsiExpressionList)) {
      return true;
    }
    PsiElement gParent = parent.getParent();
    if (gParent instanceof PsiCall call) {
      if (gParent instanceof PsiCallExpression callExpression && callExpression.getTypeArguments().length > 0) {
        return true;
      }
      JavaResolveResult result = call.resolveMethodGenerics();
      if (result instanceof MethodCandidateInfo methodCandidateInfo) {
        PsiMethod method = methodCandidateInfo.getElement();
        if (!method.hasTypeParameters()) {
          return true;
        }
        PsiExpression[] args = ((PsiExpressionList) parent).getExpressions();
        int lambdaIdx = LambdaUtil.getLambdaIdx((PsiExpressionList) parent, methodReferenceExpression);
        args[lambdaIdx] = lambdaExpression;
        final PsiParameter[] methodParams = method.getParameterList().getParameters();
        final PsiSubstitutor substitutor =
          methodCandidateInfo.inferTypeArguments(DefaultParameterTypeInferencePolicy.INSTANCE, args, true);
        PsiType formalTargetType =
          substitutor.substitute(PsiTypesUtil.getParameterType(methodParams, lambdaIdx, methodCandidateInfo.isVarargs()));
        return functionalInterfaceType.equals(FunctionalInterfaceParameterizationUtil.getGroundTargetType(formalTargetType));
      }
    }
    return false;
  }

  @Nullable
  public static String createLambdaParameterListWithFormalTypes(PsiType functionalInterfaceType, PsiLambdaExpression lambdaExpression, boolean checkApplicability) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final StringBuilder buf = new StringBuilder();
    buf.append("(");
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    LOG.assertTrue(interfaceMethod != null);
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    final PsiParameter[] lambdaParameters = lambdaExpression.getParameterList().getParameters();
    if (parameters.length != lambdaParameters.length) {
      return null;
    }
    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);
    for (int i = 0; i < parameters.length; i++) {
      PsiType psiType = substitutor.substitute(parameters[i].getType());
      if (psiType == null) {
        return null;
      }
      if (!PsiTypesUtil.isDenotableType(psiType)) {
        return null;
      }

      buf.append(checkApplicability ? psiType.getPresentableText() : psiType.getCanonicalText()).append(" ").append(lambdaParameters[i].getName());
      if (i < parameters.length - 1) {
        buf.append(", ");
      }
    }
    buf.append(")");
    return buf.toString();
  }

  @Nullable
  public static PsiParameterList specifyLambdaParameterTypes(PsiLambdaExpression lambdaExpression) {
    return specifyLambdaParameterTypes(lambdaExpression.getFunctionalInterfaceType(), lambdaExpression);
  }

  @Nullable
  public static PsiParameterList specifyLambdaParameterTypes(PsiType functionalInterfaceType, PsiLambdaExpression lambdaExpression) {
    String typedParamList = createLambdaParameterListWithFormalTypes(functionalInterfaceType, lambdaExpression, false);
    if (typedParamList != null) {
      PsiParameterList paramListWithFormalTypes = JavaPsiFacade.getElementFactory(lambdaExpression.getProject()).createMethodFromText("void foo" + typedParamList, lambdaExpression)
          .getParameterList();
      return (PsiParameterList) JavaCodeStyleManager.getInstance(lambdaExpression.getProject()).shortenClassReferences(lambdaExpression.getParameterList().replace(paramListWithFormalTypes));
    }
    return null;
  }

  public static void simplifyToExpressionLambda(@Nonnull final PsiLambdaExpression lambdaExpression) {
    final PsiElement body = lambdaExpression.getBody();
    final PsiExpression singleExpression = RedundantLambdaCodeBlockInspection.isCodeBlockRedundant(body);
    if (singleExpression != null) {
      body.replace(singleExpression);
    }
  }

  /**
   * Works for expression lambdas/one statement code block lambdas to ensures equivalent method ref -> lambda transformation.
   */
  public static void removeSideEffectsFromLambdaBody(Editor editor, PsiLambdaExpression lambdaExpression) {
    if (lambdaExpression != null && lambdaExpression.isValid()) {
      final PsiElement body = lambdaExpression.getBody();
      PsiExpression methodCall = LambdaUtil.extractSingleExpressionFromBody(body);
      PsiExpression qualifierExpression = null;
      if (methodCall instanceof PsiMethodCallExpression methodCallExpression) {
        qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
      } else if (methodCall instanceof PsiNewExpression newExpression) {
        qualifierExpression = newExpression.getQualifier();
      }

      if (qualifierExpression != null) {
        final List<PsiElement> sideEffects = new ArrayList<>();
        SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
        if (!sideEffects.isEmpty()) {
          if (Application.get().isUnitTestMode() || Messages.showYesNoDialog(
            lambdaExpression.getProject(),
            "There are possible side effects found in method reference qualifier.\nIntroduce local variable?",
            "Side Effects Detected",
            UIUtil.getQuestionIcon()
          ) == Messages.YES) {
            //ensure introduced before lambda
            qualifierExpression.putUserData(ElementToWorkOn.PARENT, lambdaExpression);
            new IntroduceVariableHandler().invoke(qualifierExpression.getProject(), editor, qualifierExpression);
          }
        }
      }
    }
  }

  /**
   * Checks whether method reference can be converted to lambda without significant semantics change
   * (i.e. method reference qualifier has no side effects)
   *
   * @param methodReferenceExpression method reference to check
   * @return true if method reference can be converted to lambda
   */
  @RequiredReadAction
  public static boolean canConvertToLambdaWithoutSideEffects(PsiMethodReferenceExpression methodReferenceExpression) {
    final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      PsiElement resolved = methodReferenceExpression.resolve();
      if (resolved == null) {
        return false;
      }
      PsiClass arrayClass = JavaPsiFacade.getInstance(methodReferenceExpression.getProject()).getElementFactory()
        .getArrayClass(PsiUtil.getLanguageLevel(methodReferenceExpression));
      return resolved == arrayClass;
    }
    return !SideEffectChecker.mayHaveSideEffects(qualifierExpression);
  }
}
