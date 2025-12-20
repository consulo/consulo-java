/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ObjectToStringInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.defaultTostringCallDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.defaultTostringCallProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ObjectToStringVisitor();
    }

    private static class ObjectToStringVisitor extends BaseInspectionVisitor {

        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            if (!ExpressionUtils.hasStringType(expression)) {
                return;
            }
            PsiExpression[] operands = expression.getOperands();
            for (PsiExpression operand : operands) {
                checkExpression(operand);
            }
        }

        @Override
        public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSEQ)) {
                return;
            }
            PsiExpression lhs = expression.getLExpression();
            if (!ExpressionUtils.hasStringType(lhs)) {
                return;
            }
            PsiExpression rhs = expression.getRExpression();
            checkExpression(rhs);
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            @NonNls String name = methodExpression.getReferenceName();
            if (HardcodedMethodConstants.TO_STRING.equals(name)) {
                PsiExpressionList argumentList = expression.getArgumentList();
                PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length != 0) {
                    return;
                }
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                checkExpression(qualifier);
            }
            else if ("append".equals(name)) {
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
                    return;
                }
                PsiExpressionList argumentList = expression.getArgumentList();
                PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length != 1) {
                    return;
                }
                PsiExpression argument = arguments[0];
                checkExpression(argument);
            }
            else if ("valueOf".equals(name)) {
                PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
                if (!(qualifierExpression instanceof PsiReferenceExpression)) {
                    return;
                }
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifierExpression;
                String canonicalText = referenceExpression.getCanonicalText();
                if (!CommonClassNames.JAVA_LANG_STRING.equals(canonicalText)) {
                    return;
                }
                PsiExpressionList argumentList = expression.getArgumentList();
                PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length != 1) {
                    return;
                }
                PsiExpression argument = arguments[0];
                checkExpression(argument);
            }
        }

        private void checkExpression(PsiExpression expression) {
            if (expression == null) {
                return;
            }
            PsiType type = expression.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            PsiClassType classType = (PsiClassType) type;
            if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                return;
            }
            PsiClass referencedClass = classType.resolve();
            if (referencedClass == null || referencedClass instanceof PsiTypeParameter) {
                return;
            }
            if (referencedClass.isEnum() || referencedClass.isInterface()) {
                return;
            }
            if (hasGoodToString(referencedClass)) {
                return;
            }
            registerError(expression);
        }

        private static boolean hasGoodToString(PsiClass aClass) {
            PsiMethod[] methods = aClass.findMethodsByName(HardcodedMethodConstants.TO_STRING, true);
            for (PsiMethod method : methods) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    continue;
                }
                String name = containingClass.getQualifiedName();
                if (CommonClassNames.JAVA_LANG_OBJECT.equals(name)) {
                    continue;
                }
                PsiParameterList parameterList = method.getParameterList();
                if (parameterList.getParametersCount() == 0) {
                    return true;
                }
            }
            return false;
        }
    }
}