/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight;

import com.intellij.java.analysis.impl.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.java.indexing.search.searches.AnnotatedMembersSearch;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class FunctionalInterfaceSuggester {
    public static final String[] FUNCTIONAL_INTERFACES = {
        //old jdk without annotations
        CommonClassNames.JAVA_LANG_RUNNABLE,
        CommonClassNames.JAVA_UTIL_CONCURRENT_CALLABLE,
        CommonClassNames.JAVA_UTIL_COMPARATOR,

        //IDEA
        "com.intellij.util.Function",
        "com.intellij.util.Consumer",
        "com.intellij.openapi.util.Computable",
        "com.intellij.openapi.util.Condition",
        "com.intellij.util.Processor",
        "com.intellij.util.Producer",

        //guava
        "com.google.common.base.Function",
        "com.google.common.base.Predicate",
        "com.google.common.base.Supplier",

        //common collections
        "org.apache.commons.collections.Closure",
        "org.apache.commons.collections.Factory",
        "org.apache.commons.collections.Predicate",
        "org.apache.commons.collections.Transformer",
    };

    public static Collection<? extends PsiType> suggestFunctionalInterfaces(@Nonnull PsiFunctionalExpression expression) {
        PsiType qualifierType = expression instanceof PsiMethodReferenceExpression methodRefExpr
            ? PsiMethodReferenceUtil.getQualifierType(methodRefExpr) : null;
        return suggestFunctionalInterfaces(expression, aClass -> composeAcceptableType(aClass, expression, qualifierType));
    }

    public static Collection<? extends PsiType> suggestFunctionalInterfaces(@Nonnull PsiMethod method) {
        return suggestFunctionalInterfaces(method, false);
    }

    public static Collection<? extends PsiType> suggestFunctionalInterfaces(
        @Nonnull PsiMethod method,
        boolean suggestUnhandledThrowables
    ) {
        if (method.isConstructor()) {
            return Collections.emptyList();
        }

        return suggestFunctionalInterfaces(
            method,
            aClass -> {
                PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
                if (interfaceMethod != null) {
                    PsiParameter[] parameters = method.getParameterList().getParameters();
                    PsiParameter[] interfaceMethodParameters = interfaceMethod.getParameterList().getParameters();
                    if (parameters.length != interfaceMethodParameters.length) {
                        return Collections.emptyList();
                    }

                    PsiType[] left = new PsiType[parameters.length + 1];
                    PsiType[] right = new PsiType[parameters.length + 1];

                    for (int i = 0; i < parameters.length; i++) {
                        left[i] = interfaceMethodParameters[i].getType();
                        right[i] = parameters[i].getType();
                    }

                    left[parameters.length] = method.getReturnType();
                    right[parameters.length] = interfaceMethod.getReturnType();

                    PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
                    PsiSubstitutor substitutor = PsiResolveHelper.getInstance(aClass.getProject())
                        .inferTypeArguments(typeParameters, left, right, PsiUtil.getLanguageLevel(method));
                    if (PsiUtil.isRawSubstitutor(aClass, substitutor)) {
                        return Collections.emptyList();
                    }

                    for (int i = 0; i < interfaceMethodParameters.length; i++) {
                        PsiType paramType = parameters[i].getType();
                        PsiType interfaceParamType = substitutor.substitute(interfaceMethodParameters[i].getType());
                        if (!(interfaceParamType instanceof PsiPrimitiveType
                            ? paramType.equals(interfaceParamType)
                            : TypeConversionUtil.isAssignable(paramType, interfaceParamType))) {
                            return Collections.emptyList();
                        }
                    }

                    PsiType returnType = method.getReturnType();
                    PsiType interfaceMethodReturnType = substitutor.substitute(interfaceMethod.getReturnType());
                    if (returnType != null && !TypeConversionUtil.isAssignable(returnType, interfaceMethodReturnType)) {
                        return Collections.emptyList();
                    }

                    if (interfaceMethodReturnType instanceof PsiPrimitiveType && !interfaceMethodReturnType.equals(returnType)) {
                        return Collections.emptyList();
                    }

                    PsiClassType[] interfaceThrownTypes = interfaceMethod.getThrowsList().getReferencedTypes();
                    PsiClassType[] thrownTypes = method.getThrowsList().getReferencedTypes();
                    for (PsiClassType thrownType : thrownTypes) {
                        if (!ExceptionUtil.isHandledBy(thrownType, interfaceThrownTypes, substitutor)) {
                            return Collections.emptyList();
                        }
                    }

                    if (!suggestUnhandledThrowables) {
                        for (PsiClassType thrownType : interfaceThrownTypes) {
                            PsiCodeBlock codeBlock = PsiTreeUtil.getContextOfType(method, PsiCodeBlock.class);
                            PsiType substitutedThrowable = substitutor.substitute(thrownType);
                            if (codeBlock == null || !(substitutedThrowable instanceof PsiClassType)
                                || !ExceptionUtil.isHandled((PsiClassType)substitutedThrowable, codeBlock)) {
                                return Collections.emptyList();
                            }
                        }
                    }

                    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
                    return Collections.singletonList(elementFactory.createType(aClass, substitutor));
                }
                return Collections.emptyList();
            }
        );
    }

    private static <T extends PsiElement> Collection<? extends PsiType> suggestFunctionalInterfaces(
        @Nonnull T element,
        Function<? super PsiClass, ? extends List<? extends PsiType>> acceptanceChecker
    ) {
        Project project = element.getProject();
        Set<PsiType> types = new HashSet<>();
        Predicate<PsiMember> consumer = member -> {
            if (member instanceof PsiClass
                && Java15APIUsageInspection.getLastIncompatibleLanguageLevel(member, PsiUtil.getLanguageLevel(element)) == null) {
                if (!JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible(member, element, null)) {
                    return true;
                }
                types.addAll(acceptanceChecker.apply((PsiClass)member));
            }
            return true;
        };
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
        PsiClass functionalInterfaceClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, allScope);
        if (functionalInterfaceClass != null) {
            AnnotatedMembersSearch.search(functionalInterfaceClass, element.getResolveScope()).forEach(consumer);
        }

        for (String functionalInterface : FUNCTIONAL_INTERFACES) {
            PsiClass aClass = psiFacade.findClass(functionalInterface, element.getResolveScope());
            if (aClass != null) {
                consumer.test(aClass);
            }
        }

        ArrayList<PsiType> typesToSuggest = new ArrayList<>(types);
        Collections.sort(typesToSuggest, Comparator.comparing(PsiType::getCanonicalText));
        return typesToSuggest;
    }

    @RequiredReadAction
    private static List<? extends PsiType> composeAcceptableType(
        @Nonnull PsiClass interface2Consider,
        @Nonnull PsiFunctionalExpression expression,
        PsiType qualifierType
    ) {
        PsiType type =
            JavaPsiFacade.getElementFactory(interface2Consider.getProject()).createType(interface2Consider, PsiSubstitutor.EMPTY);
        if (expression.isAcceptable(type)) {
            return Collections.singletonList(type);
        }

        return composeAcceptableType1(interface2Consider, expression, qualifierType);
    }

    @Nonnull
    @RequiredReadAction
    private static List<? extends PsiType> composeAcceptableType1(
        PsiClass interface2Consider,
        PsiFunctionalExpression expression,
        PsiType qualifierType
    ) {
        if (interface2Consider.hasTypeParameters()) {
            PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interface2Consider);
            if (interfaceMethod != null) {
                PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
                Project project = interface2Consider.getProject();
                PsiType returnType = interfaceMethod.getReturnType();
                if (expression instanceof PsiLambdaExpression lambda && lambda.hasFormalParameterTypes()) {
                    PsiParameter[] functionalExprParameters = lambda.getParameterList().getParameters();
                    if (parameters.length != functionalExprParameters.length) {
                        return Collections.emptyList();
                    }

                    PsiType[] left = new PsiType[parameters.length + 1];
                    PsiType[] right = new PsiType[parameters.length + 1];
                    for (int i = 0; i < functionalExprParameters.length; i++) {
                        left[i] = parameters[i].getType();
                        right[i] = functionalExprParameters[i].getType();
                    }

                    List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambda);
                    left[parameters.length] = returnExpressions.isEmpty() ? PsiType.VOID : returnExpressions.get(0).getType();
                    right[parameters.length] = returnType;

                    PsiSubstitutor substitutor = PsiResolveHelper.getInstance(project)
                        .inferTypeArguments(interface2Consider.getTypeParameters(), left, right, PsiUtil.getLanguageLevel(expression));

                    PsiType type = JavaPsiFacade.getElementFactory(project).createType(interface2Consider, substitutor);

                    if (expression.isAcceptable(type)) {
                        return Collections.singletonList(type);
                    }
                }
                else if (expression instanceof PsiMethodReferenceExpression methodRefExpr) {
                    List<PsiType> types = new ArrayList<>();
                    for (JavaResolveResult result : methodRefExpr.multiResolve(true)) {
                        PsiElement element = result.getElement();
                        if (element instanceof PsiMethod method) {
                            int offset = hasOffset(methodRefExpr, method) ? 1 : 0;
                            PsiParameter[] targetMethodParameters = method.getParameterList().getParameters();
                            if (targetMethodParameters.length + offset == parameters.length) {
                                PsiType[] left = new PsiType[parameters.length + 1];
                                PsiType[] right = new PsiType[parameters.length + 1];
                                if (offset > 0) {
                                    if (qualifierType == null) {
                                        continue;
                                    }
                                    left[0] = parameters[0].getType();
                                    right[0] = qualifierType;
                                }

                                for (int i = 0; i < targetMethodParameters.length; i++) {
                                    left[i + offset] = parameters[i + offset].getType();
                                    right[i + offset] = targetMethodParameters[i].getType();
                                }

                                left[parameters.length] = method.isConstructor() ? qualifierType : method.getReturnType();
                                right[parameters.length] = returnType;

                                PsiSubstitutor substitutor = PsiResolveHelper.getInstance(project).inferTypeArguments(
                                    interface2Consider.getTypeParameters(),
                                    left,
                                    right,
                                    PsiUtil.getLanguageLevel(methodRefExpr)
                                );

                                PsiType type = JavaPsiFacade.getElementFactory(project).createType(interface2Consider, substitutor);

                                if (methodRefExpr.isAcceptable(type)) {
                                    types.add(type);
                                }
                            }
                        }
                    }
                    return types;
                }
            }
        }
        return Collections.emptyList();
    }

    private static boolean hasOffset(PsiMethodReferenceExpression expression, PsiMethod method) {
        return PsiMethodReferenceUtil.isStaticallyReferenced(expression) && !method.isStatic() && !method.isConstructor();
    }
}
