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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.localize.JavaCompilationErrorLocalize;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author anna
 */
public class LambdaHighlightingUtil {
    private static final Logger LOG = Logger.getInstance(LambdaHighlightingUtil.class);

    @Nonnull
    public static LocalizeValue checkInterfaceFunctional(@Nonnull PsiClass psiClass) {
        return checkInterfaceFunctional(psiClass, JavaCompilationErrorLocalize.lambdaTargetNotInterface());
    }

    @Nonnull
    public static LocalizeValue checkInterfaceFunctional(@Nonnull PsiClass psiClass, @Nonnull LocalizeValue interfaceNonFunctionalMessage) {
        if (psiClass instanceof PsiTypeParameter) {
            return LocalizeValue.empty(); //should be logged as cyclic inference
        }
        List<HierarchicalMethodSignature> signatures = LambdaUtil.findFunctionCandidates(psiClass);
        if (signatures == null) {
            return interfaceNonFunctionalMessage;
        }
        if (signatures.isEmpty()) {
            return JavaCompilationErrorLocalize.lambdaNoTargetMethodFound();
        }
        if (signatures.size() == 1) {
            return LocalizeValue.empty();
        }
        return JavaCompilationErrorLocalize.lambdaMultipleSamCandidates(HighlightUtil.formatClass(psiClass));
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkParametersCompatible(
        PsiLambdaExpression expression,
        PsiParameter[] methodParams,
        PsiSubstitutor substitutor
    ) {
        PsiParameter[] lambdaParams = expression.getParameterList().getParameters();
        if (lambdaParams.length != methodParams.length) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression.getParameterList())
                .descriptionAndTooltip(JavaCompilationErrorLocalize.lambdaWrongNumberOfParameters(methodParams.length, lambdaParams.length))
                .create();
        }
        boolean hasFormalParameterTypes = expression.hasFormalParameterTypes();
        for (int i = 0; i < lambdaParams.length; i++) {
            PsiParameter lambdaParameter = lambdaParams[i];
            PsiType lambdaParameterType = lambdaParameter.getType();
            PsiType substitutedParamType = substitutor.substitute(methodParams[i].getType());
            if (hasFormalParameterTypes && !PsiTypesUtil.compareTypes(lambdaParameterType, substitutedParamType, true)
                || !TypeConversionUtil.isAssignable(substitutedParamType, lambdaParameterType)) {
                String expectedType = substitutedParamType != null ? substitutedParamType.getPresentableText() : null;
                String actualType = lambdaParameterType.getPresentableText();
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression.getParameterList())
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.lambdaIncompatibleParameterTypes(expectedType, actualType))
                    .create();
            }
        }
        return null;
    }

    public static boolean insertSemicolonAfter(PsiLambdaExpression lambdaExpression) {
        return lambdaExpression.getBody() instanceof PsiCodeBlock || !insertSemicolon(lambdaExpression.getParent());
    }

    public static boolean insertSemicolon(PsiElement parent) {
        return parent instanceof PsiExpressionList || parent instanceof PsiExpression;
    }

    @Nonnull
    public static LocalizeValue checkInterfaceFunctional(PsiType functionalInterfaceType) {
        if (functionalInterfaceType instanceof PsiIntersectionType) {
            Set<MethodSignature> signatures = new HashSet<>();
            for (PsiType type : ((PsiIntersectionType) functionalInterfaceType).getConjuncts()) {
                if (checkInterfaceFunctional(type).isEmpty()) {
                    MethodSignature signature = LambdaUtil.getFunction(PsiUtil.resolveClassInType(type));
                    LOG.assertTrue(signature != null, type.getCanonicalText());
                    signatures.add(signature);
                }
            }

            if (signatures.size() > 1) {
                return JavaCompilationErrorLocalize.lambdaMultipleSamCandidates(functionalInterfaceType.getPresentableText());
            }
            return LocalizeValue.empty();
        }
        PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        PsiClass aClass = resolveResult.getElement();
        if (aClass != null) {
            if (aClass instanceof PsiTypeParameter) {
                return LocalizeValue.empty(); //should be logged as cyclic inference
            }
            List<HierarchicalMethodSignature> signatures = LambdaUtil.findFunctionCandidates(aClass);
            if (signatures != null && signatures.size() == 1) {
                MethodSignature functionalMethod = signatures.get(0);
                if (functionalMethod.getTypeParameters().length > 0) {
                    return JavaCompilationErrorLocalize.lambdaSamGeneric();
                }
            }
            if (checkReturnTypeApplicable(resolveResult, aClass)) {
                return LocalizeValue.localizeTODO(
                    "No instance of type " + functionalInterfaceType.getPresentableText() +
                        " exists so that lambda expression can be type-checked"
                );
            }
            return checkInterfaceFunctional(aClass);
        }
        return JavaCompilationErrorLocalize.lambdaNotAFunctionalInterface(functionalInterfaceType.getPresentableText());
    }

    private static boolean checkReturnTypeApplicable(PsiClassType.ClassResolveResult resolveResult, final PsiClass aClass) {
        MethodSignature methodSignature = LambdaUtil.getFunction(aClass);
        if (methodSignature == null) {
            return false;
        }

        for (PsiTypeParameter parameter : aClass.getTypeParameters()) {
            if (parameter.getExtendsListTypes().length == 0) {
                continue;
            }
            PsiType substitution = resolveResult.getSubstitutor().substitute(parameter);
            if (substitution instanceof PsiWildcardType wildcardType && !wildcardType.isBounded()) {
                boolean depends = false;
                for (PsiType paramType : methodSignature.getParameterTypes()) {
                    if (LambdaUtil.depends(
                        paramType,
                        new LambdaUtil.TypeParamsChecker((PsiMethod) null, aClass) {
                            @Override
                            public boolean startedInference() {
                                return true;
                            }
                        },
                        parameter
                    )) {
                        depends = true;
                        break;
                    }
                }
                if (!depends) {
                    return true;
                }
            }
        }
        return false;
    }
}
