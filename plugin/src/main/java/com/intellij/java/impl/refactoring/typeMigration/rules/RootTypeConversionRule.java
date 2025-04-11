/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration.rules;

import com.intellij.java.impl.ig.style.UnnecessarilyQualifiedStaticUsageInspection;
import com.intellij.java.impl.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.java.impl.refactoring.typeMigration.TypeEvaluator;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;

import java.util.Arrays;

/**
 * @author anna
 */
public class RootTypeConversionRule extends TypeConversionRule {
    @Override
    @RequiredUIAccess
    public TypeConversionDescriptorBase findConversion(
        PsiType from,
        PsiType to,
        PsiMember member,
        PsiExpression context,
        TypeMigrationLabeler labeler
    ) {
        if (member != null && to instanceof PsiClassType toClassType && from instanceof PsiClassType fromClassType) {
            PsiClass targetClass = toClassType.resolve();
            if (targetClass != null && member.isPhysical()) {
                if (member instanceof PsiMethod method) {
                    PsiMethod replacer = targetClass.findMethodBySignature(method, true);
                    if (replacer == null) {
                        for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
                            replacer = targetClass.findMethodBySignature(superMethod, true);
                            if (replacer != null) {
                                method = superMethod;
                                break;
                            }
                        }
                    }
                    if (replacer != null) {
                        boolean isStaticMethodConversion = replacer.isStatic();
                        boolean isValid = isStaticMethodConversion
                            ? TypeConversionUtil.areTypesConvertible(method.getReturnType(), fromClassType)
                            && TypeConversionUtil.areTypesConvertible(replacer.getReturnType(), toClassType)
                            : TypeConversionUtil.areTypesConvertible(method.getReturnType(), replacer.getReturnType());
                        if (isValid) {
                            PsiElement parent = context.getParent();
                            if (context instanceof PsiMethodReferenceExpression methodRefExpr
                                && Comparing.equal(methodRefExpr.getFunctionalInterfaceType(), toClassType)
                                && method.isEquivalentTo(LambdaUtil.getFunctionalInterfaceMethod(fromClassType))) {
                                return new TypeConversionDescriptorBase() {
                                    @Override
                                    public PsiExpression replace(
                                        PsiExpression expression,
                                        @Nonnull TypeEvaluator evaluator
                                    ) throws IncorrectOperationException {
                                        PsiMethodReferenceExpression methodReferenceExpression =
                                            (PsiMethodReferenceExpression)expression;
                                        PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
                                        if (qualifierExpression != null) {
                                            return (PsiExpression)expression.replace(qualifierExpression);
                                        }
                                        else {
                                            return expression;
                                        }
                                    }
                                };
                            }
                            if (context instanceof PsiReferenceExpression refExpr && parent instanceof PsiMethodCallExpression methodCall) {
                                JavaResolveResult resolveResult = refExpr.advancedResolve(false);
                                PsiSubstitutor aSubst;
                                PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
                                PsiClass substitutionClass = method.getContainingClass();
                                if (qualifier != null) {
                                    PsiType evaluatedQualifierType = labeler.getTypeEvaluator().evaluateType(qualifier);
                                    if (evaluatedQualifierType instanceof PsiClassType qualifierClassType) {
                                        aSubst = qualifierClassType.resolveGenerics().getSubstitutor();
                                    }
                                    else {
                                        aSubst = PsiSubstitutor.EMPTY;
                                    }
                                }
                                else {
                                    aSubst = TypeConversionUtil.getClassSubstitutor(
                                        member.getContainingClass(),
                                        substitutionClass,
                                        PsiSubstitutor.EMPTY
                                    );
                                }

                                PsiParameter[] originalParams = ((PsiMethod)member).getParameterList().getParameters();
                                PsiParameter[] migrationParams = replacer.getParameterList().getParameters();
                                PsiExpression[] actualParams = ((PsiMethodCallExpression)parent).getArgumentList().getExpressions();

                                assert originalParams.length == migrationParams.length;
                                PsiSubstitutor methodTypeParamsSubstitutor = labeler.getTypeEvaluator().createMethodSubstitution(
                                    originalParams,
                                    actualParams,
                                    method,
                                    context,
                                    aSubst != null ? aSubst : PsiSubstitutor.EMPTY,
                                    true
                                );
                                for (int i = 0; i < originalParams.length; i++) {
                                    PsiType originalType = resolveResult.getSubstitutor().substitute(originalParams[i].getType());

                                    PsiType type = migrationParams[i].getType();
                                    if (InheritanceUtil.isInheritorOrSelf(targetClass, substitutionClass, true)) {
                                        PsiSubstitutor superClassSubstitutor =
                                            TypeConversionUtil.getClassSubstitutor(substitutionClass, targetClass, PsiSubstitutor.EMPTY);
                                        assert (superClassSubstitutor != null);
                                        type = superClassSubstitutor.substitute(type);
                                    }

                                    PsiType migrationType = methodTypeParamsSubstitutor.substitute(type);
                                    if (!originalType.equals(migrationType) && !areParametersAssignable(migrationType, i, actualParams)) {
                                        if (migrationType instanceof PsiEllipsisType && actualParams.length != migrationParams.length) {
                                            return null;
                                        }
                                        labeler.migrateExpressionType(actualParams[i], migrationType, context, false, true);
                                    }
                                }
                            }
                            return isStaticMethodConversion
                                ? new MyStaticMethodConversionDescriptor(targetClass)
                                : new TypeConversionDescriptorBase();
                        }
                    }
                }
                else if (member instanceof PsiField field) {
                    PsiClass fieldContainingClass = field.getContainingClass();
                    if (InheritanceUtil.isInheritorOrSelf(targetClass, fieldContainingClass, true)) {
                        return new TypeConversionDescriptorBase();
                    }
                }
            }
        }
        return null;
    }

    private static boolean areParametersAssignable(PsiType migrationType, int paramId, PsiExpression[] actualParams) {
        if (migrationType instanceof PsiEllipsisType) {
            if (actualParams.length == paramId) {
                // no arguments for ellipsis
                return true;
            }
            else if (actualParams.length == paramId + 1) {
                // only one argument for ellipsis
                return TypeConversionUtil.areTypesAssignmentCompatible(migrationType, actualParams[paramId])
                    || TypeConversionUtil.areTypesAssignmentCompatible(
                    ((PsiEllipsisType)migrationType).getComponentType(),
                    actualParams[paramId]
                );
            }
            else if (actualParams.length > paramId + 1) {
                // few arguments
                PsiType componentType = ((PsiEllipsisType)migrationType).getComponentType();
                for (int i = paramId; i < actualParams.length; i++) {
                    if (!TypeConversionUtil.areTypesAssignmentCompatible(componentType, actualParams[i])) {
                        return false;
                    }
                }
                return true;
            }
            throw new AssertionError(
                " migrationType: " + migrationType +
                    ", paramId: " + paramId +
                    ", actualParameters: " + Arrays.toString(actualParams)
            );
        }
        else {
            return paramId >= actualParams.length || TypeConversionUtil.areTypesAssignmentCompatible(migrationType, actualParams[paramId]);
        }
    }

    private static class MyStaticMethodConversionDescriptor extends TypeConversionDescriptorBase {
        @Nonnull
        private final String myTargetClassQName;

        private MyStaticMethodConversionDescriptor(PsiClass replacer) {
            myTargetClassQName = replacer.getQualifiedName();
        }

        @Override
        @RequiredWriteAction
        public PsiExpression replace(PsiExpression expression, @Nonnull TypeEvaluator evaluator) throws IncorrectOperationException {
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
            PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
            PsiMethodCallExpression newMethodCall;
            if (qualifierExpression != null) {
                JavaCodeStyleManager.getInstance(expression.getProject()).shortenClassReferences(
                    qualifierExpression.replace(elementFactory.createExpressionFromText(myTargetClassQName, expression))
                );
                newMethodCall = methodCallExpression;
            }
            else {
                newMethodCall = (PsiMethodCallExpression)expression.replace(elementFactory.createExpressionFromText(
                    myTargetClassQName + "." + expression.getText(),
                    expression
                ));
            }
            if (UnnecessarilyQualifiedStaticUsageInspection.isUnnecessarilyQualifiedAccess(
                newMethodCall.getMethodExpression(),
                false,
                false,
                false
            )) {
                newMethodCall.getMethodExpression().getQualifierExpression().delete();
            }
            return newMethodCall;
        }

        @Override
        public String toString() {
            return "Static method qualifier conversion -> " + myTargetClassQName;
        }
    }
}