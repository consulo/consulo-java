/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import com.intellij.java.language.psi.util.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.localize.JavaCompilationErrorLocalize;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author anna
 */
public class PsiMethodReferenceUtil {
    private static final Logger LOG = Logger.getInstance(PsiMethodReferenceUtil.class);

    @RequiredReadAction
    public static boolean isSecondSearchPossible(
        PsiType[] parameterTypes,
        QualifierResolveResult qualifierResolveResult,
        PsiMethodReferenceExpression methodRef
    ) {
        return parameterTypes.length > 0
            && !(parameterTypes[0] instanceof PsiPrimitiveType)
            && !methodRef.isConstructor()
            && isStaticallyReferenced(methodRef)
            && isReceiverType(parameterTypes[0], qualifierResolveResult.getContainingClass(), qualifierResolveResult.getSubstitutor());
    }

    @RequiredReadAction
    public static boolean isResolvedBySecondSearch(
        @Nonnull PsiMethodReferenceExpression methodRef,
        @Nullable MethodSignature signature,
        boolean varArgs,
        boolean isStatic,
        int parametersCount
    ) {
        if (signature == null) {
            return false;
        }
        QualifierResolveResult qualifierResolveResult = getQualifierResolveResult(methodRef);
        PsiType[] functionalMethodParameterTypes = signature.getParameterTypes();
        return (parametersCount + 1 == functionalMethodParameterTypes.length && !varArgs || varArgs && functionalMethodParameterTypes.length > 0 && !isStatic)
            && isSecondSearchPossible(functionalMethodParameterTypes, qualifierResolveResult, methodRef);
    }

    @Nullable
    public static PsiType getQualifierType(PsiMethodReferenceExpression expression) {
        PsiTypeElement typeElement = expression.getQualifierType();
        if (typeElement != null) {
            return typeElement.getType();
        }
        else {
            PsiType qualifierType = null;
            PsiElement qualifier = expression.getQualifier();
            if (qualifier instanceof PsiExpression qualifierExpr) {
                qualifierType = qualifierExpr.getType();
            }
            if (qualifierType == null && qualifier instanceof PsiReferenceExpression qRefExpr) {
                return JavaPsiFacade.getElementFactory(expression.getProject()).createType(qRefExpr);
            }
            return qualifierType;
        }
    }

    @RequiredReadAction
    public static boolean isReturnTypeCompatible(
        PsiMethodReferenceExpression expression,
        JavaResolveResult result,
        PsiType functionalInterfaceType
    ) {
        return isReturnTypeCompatible(expression, result, functionalInterfaceType, null);
    }

    @RequiredReadAction
    private static boolean isReturnTypeCompatible(
        PsiMethodReferenceExpression expression,
        JavaResolveResult result,
        PsiType functionalInterfaceType,
        SimpleReference<LocalizeValue> errorMessage
    ) {
        PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
        if (interfaceMethod != null) {
            PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);

            if (PsiType.VOID.equals(interfaceReturnType) || interfaceReturnType == null) {
                return true;
            }

            PsiSubstitutor subst = result.getSubstitutor();

            PsiType methodReturnType = null;
            PsiClass containingClass = null;
            PsiElement resolve = result.getElement();
            if (resolve instanceof PsiMethod method) {
                containingClass = method.getContainingClass();
                methodReturnType = PsiTypesUtil.patchMethodGetClassReturnType(expression, method);
                if (methodReturnType == null) {
                    methodReturnType = method.getReturnType();
                    if (PsiType.VOID.equals(methodReturnType)) {
                        return false;
                    }

                    PsiClass qContainingClass = getQualifierResolveResult(expression).getContainingClass();
                    if (qContainingClass != null && containingClass != null
                        && isReceiverType(getFirstParameterType(functionalInterfaceType, expression), qContainingClass, subst)) {
                        subst = TypeConversionUtil.getClassSubstitutor(containingClass, qContainingClass, subst);
                        LOG.assertTrue(subst != null);
                    }

                    methodReturnType = subst.substitute(methodReturnType);
                }
            }
            else if (resolve instanceof PsiClass psiClass) {
                if (PsiEquivalenceUtil.areElementsEquivalent(
                    psiClass,
                    JavaPsiFacade.getElementFactory(expression.getProject()).getArrayClass(PsiUtil.getLanguageLevel(expression))
                )) {
                    PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
                    if (typeParameters.length == 1) {
                        PsiType arrayComponentType = subst.substitute(typeParameters[0]);
                        if (arrayComponentType == null) {
                            return false;
                        }
                        methodReturnType = arrayComponentType.createArrayType();
                    }
                }
                containingClass = (PsiClass) resolve;
            }

            if (methodReturnType == null) {
                if (containingClass == null) {
                    return false;
                }
                methodReturnType = JavaPsiFacade.getElementFactory(expression.getProject()).createType(containingClass, subst);
            }

            methodReturnType = PsiUtil.captureToplevelWildcards(methodReturnType, expression);

            if (TypeConversionUtil.isAssignable(interfaceReturnType, methodReturnType)) {
                return true;
            }

            if (errorMessage != null) {
                errorMessage.set(LocalizeValue.localizeTODO(
                    "Bad return type in method reference: " +
                        "cannot convert " + methodReturnType.getCanonicalText() + " to " + interfaceReturnType.getCanonicalText()
                ));
            }
        }
        return false;
    }

    public static class QualifierResolveResult {
        private final PsiClass myContainingClass;
        private final PsiSubstitutor mySubstitutor;
        private final boolean myReferenceTypeQualified;

        public QualifierResolveResult(PsiClass containingClass, PsiSubstitutor substitutor, boolean referenceTypeQualified) {
            myContainingClass = containingClass;
            mySubstitutor = substitutor;
            myReferenceTypeQualified = referenceTypeQualified;
        }

        @Nullable
        public PsiClass getContainingClass() {
            return myContainingClass;
        }

        public PsiSubstitutor getSubstitutor() {
            return mySubstitutor;
        }

        public boolean isReferenceTypeQualified() {
            return myReferenceTypeQualified;
        }
    }

    @RequiredReadAction
    public static boolean isValidQualifier(PsiMethodReferenceExpression expression) {
        if (expression.getReferenceNameElement() instanceof PsiKeyword) {
            PsiElement qualifier = expression.getQualifier();
            if (qualifier instanceof PsiTypeElement) {
                return true;
            }
            if (qualifier instanceof PsiReferenceExpression qRefExpr && qRefExpr.resolve() instanceof PsiClass) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    @RequiredReadAction
    public static QualifierResolveResult getQualifierResolveResult(@Nonnull PsiMethodReferenceExpression methodReferenceExpression) {
        PsiClass containingClass = null;
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        PsiExpression expression = methodReferenceExpression.getQualifierExpression();
        if (expression != null) {
            PsiType expressionType = expression.getType();
            if (expressionType instanceof PsiCapturedWildcardType capturedWildcardType) {
                expressionType = capturedWildcardType.getUpperBound();
            }
            else {
                expressionType = replaceArrayType(expressionType, expression);
            }
            PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(expressionType);
            containingClass = result.getElement();
            if (containingClass != null) {
                substitutor = result.getSubstitutor();
            }
            if (containingClass == null && expression instanceof PsiReferenceExpression refExpr) {
                JavaResolveResult resolveResult = refExpr.advancedResolve(false);
                if (resolveResult.getElement() instanceof PsiClass psiClass) {
                    containingClass = psiClass;
                    substitutor = resolveResult.getSubstitutor();
                    return new QualifierResolveResult(containingClass, substitutor, true);
                }
            }
        }
        else {
            PsiTypeElement typeElement = methodReferenceExpression.getQualifierType();
            if (typeElement != null) {
                PsiType type = replaceArrayType(typeElement.getType(), typeElement);
                PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
                containingClass = result.getElement();
                if (containingClass != null) {
                    return new QualifierResolveResult(containingClass, result.getSubstitutor(), true);
                }
            }
        }
        return new QualifierResolveResult(containingClass, substitutor, false);
    }

    @RequiredReadAction
    public static boolean isStaticallyReferenced(@Nonnull PsiMethodReferenceExpression methodReferenceExpression) {
        PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
        return qualifierExpression == null
            || qualifierExpression instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiClass;
    }

    //if P1, ..., Pn is not empty and P1 is a subtype of ReferenceType, then the method reference expression is treated as
    // if it were a method invocation expression with argument expressions of types P2, ...,Pn.
    @RequiredReadAction
    public static boolean isReceiverType(@Nullable PsiType receiverType, PsiClass containingClass, PsiSubstitutor psiSubstitutor) {
        if (receiverType == null) {
            return false;
        }
        return TypeConversionUtil.isAssignable(
            JavaPsiFacade.getElementFactory(containingClass.getProject()).createType(containingClass, psiSubstitutor),
            replaceArrayType(receiverType, containingClass)
        );
    }

    public static PsiType getFirstParameterType(PsiType functionalInterfaceType, PsiElement context) {
        PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        MethodSignature function = LambdaUtil.getFunction(resolveResult.getElement());
        if (function != null) {
            int interfaceMethodParamsLength = function.getParameterTypes().length;
            if (interfaceMethodParamsLength > 0) {
                PsiType type = resolveResult.getSubstitutor().substitute(function.getParameterTypes()[0]);
                return type != null ? PsiUtil.captureToplevelWildcards(type, context) : null;
            }
        }
        return null;
    }

    @RequiredReadAction
    private static PsiType replaceArrayType(PsiType type, @Nonnull PsiElement context) {
        if (type instanceof PsiArrayType arrayType) {
            type = JavaPsiFacade.getElementFactory(context.getProject())
                .getArrayClassType(arrayType.getComponentType(), PsiUtil.getLanguageLevel(context));
        }
        return type;
    }

    @Nonnull
    @RequiredReadAction
    public static LocalizeValue checkMethodReferenceContext(PsiMethodReferenceExpression methodRef) {
        PsiElement resolve = methodRef.resolve();

        if (resolve == null) {
            return LocalizeValue.empty();
        }
        return checkMethodReferenceContext(methodRef, resolve, methodRef.getFunctionalInterfaceType());
    }

    @Nonnull
    @RequiredReadAction
    public static LocalizeValue checkMethodReferenceContext(
        PsiMethodReferenceExpression methodRef,
        PsiElement resolve,
        PsiType functionalInterfaceType
    ) {
        PsiClass containingClass = resolve instanceof PsiMethod method ? method.getContainingClass() : (PsiClass) resolve;
        boolean isStaticSelector = isStaticallyReferenced(methodRef);
        PsiElement qualifier = methodRef.getQualifier();

        boolean isMethodStatic = false;
        boolean receiverReferenced = false;
        boolean isConstructor = true;

        if (resolve instanceof PsiMethod method) {
            isMethodStatic = method.isStatic();
            isConstructor = method.isConstructor();

            PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
            PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
            receiverReferenced = isResolvedBySecondSearch(
                methodRef,
                interfaceMethod != null ? interfaceMethod.getSignature(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)) : null,
                method.isVarArgs(),
                isMethodStatic,
                method.getParameterList().getParametersCount()
            );

            if (method.isAbstract() && qualifier instanceof PsiSuperExpression) {
                return JavaCompilationErrorLocalize.methodReferenceAbstractMethod(method.getName());
            }
        }

        if (!receiverReferenced && isStaticSelector && !isMethodStatic && !isConstructor) {
            return JavaCompilationErrorLocalize.methodReferenceNonStaticMethodInStaticContext();
        }

        if (!receiverReferenced && !isStaticSelector && isMethodStatic) {
            return JavaCompilationErrorLocalize.methodReferenceStaticMethodNonStaticQualifier();
        }

        if (receiverReferenced && isStaticSelector && isMethodStatic && !isConstructor) {
            return JavaCompilationErrorLocalize.methodReferenceStaticMethodReceiver();
        }

        if (isMethodStatic && isStaticSelector && qualifier instanceof PsiTypeElement) {
            PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getChildOfType(qualifier, PsiJavaCodeReferenceElement.class);
            if (referenceElement != null) {
                PsiReferenceParameterList parameterList = referenceElement.getParameterList();
                if (parameterList != null && parameterList.getTypeArguments().length > 0) {
                    return JavaCompilationErrorLocalize.methodReferenceParameterizedQualifier();
                }
            }
        }

        if (isConstructor) {
            if (containingClass != null && PsiUtil.isInnerClass(containingClass) && containingClass.isPhysical()) {
                PsiClass outerClass = containingClass.getContainingClass();
                if (outerClass != null && !InheritanceUtil.hasEnclosingInstanceInScope(outerClass, methodRef, true, false)) {
                    return JavaCompilationErrorLocalize.methodReferenceEnclosingInstanceNotInScope(
                        PsiFormatUtil.formatClass(outerClass, PsiFormatUtilBase.SHOW_NAME)
                    );
                }
            }
        }
        return LocalizeValue.empty();
    }

    @Nonnull
    public static LocalizeValue checkTypeArguments(PsiTypeElement qualifier, PsiType psiType) {
        if (psiType instanceof PsiClassType) {
            PsiJavaCodeReferenceElement referenceElement = qualifier.getInnermostComponentReferenceElement();
            if (referenceElement != null) {
                PsiType[] typeParameters = referenceElement.getTypeParameters();
                for (PsiType typeParameter : typeParameters) {
                    if (typeParameter instanceof PsiWildcardType) {
                        return JavaCompilationErrorLocalize.methodReferenceQualifierWildcard();
                    }
                }
            }
        }
        return LocalizeValue.empty();
    }

    @Nonnull
    @RequiredReadAction
    public static LocalizeValue checkReturnType(
        PsiMethodReferenceExpression expression,
        JavaResolveResult result,
        PsiType functionalInterfaceType
    ) {
        SimpleReference<LocalizeValue> errorMessage = SimpleReference.create(LocalizeValue.empty());
        if (!isReturnTypeCompatible(expression, result, functionalInterfaceType, errorMessage)) {
            return errorMessage.get();
        }
        return LocalizeValue.empty();
    }
}
