/*
 * Copyright 2008-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class RedundantStringFormatCallInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.redundantStringFormatCallDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.redundantStringFormatCallProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new RedundantStringFormatCallFix();
    }

    private static class RedundantStringFormatCallFix extends InspectionGadgetsFix {
        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.redundantStringFormatCallQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
            PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            PsiExpression lastArgument = arguments[arguments.length - 1];
            methodCallExpression.replace(lastArgument);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new RedundantStringFormatCallVisitor();
    }

    private static class RedundantStringFormatCallVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!"format".equals(methodName)) {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length > 2 || arguments.length == 0) {
                return;
            }
            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            String className = aClass.getQualifiedName();
            if (!CommonClassNames.JAVA_LANG_STRING.equals(className)) {
                return;
            }
            PsiExpression firstArgument = arguments[0];
            PsiType firstType = firstArgument.getType();
            if (firstType == null || containsPercentN(firstArgument)) {
                return;
            }
            if (firstType.equalsToText(CommonClassNames.JAVA_LANG_STRING) && arguments.length == 1) {
                registerMethodCallError(expression);
            }
            else if (firstType.equalsToText("java.util.Locale")) {
                if (arguments.length != 2) {
                    return;
                }
                PsiExpression secondArgument = arguments[1];
                PsiType secondType = secondArgument.getType();
                if (secondType == null) {
                    return;
                }
                if (secondType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                    registerMethodCallError(expression);
                }
            }
        }

        private static boolean containsPercentN(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PsiLiteralExpression) {
                PsiLiteralExpression literalExpression = (PsiLiteralExpression) expression;
                String expressionText = literalExpression.getText();
                return expressionText.contains("%n");
            }
            if (expression instanceof PsiPolyadicExpression) {
                PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
                IElementType tokenType = polyadicExpression.getOperationTokenType();
                if (!tokenType.equals(JavaTokenType.PLUS)) {
                    return false;
                }
                PsiExpression[] operands = polyadicExpression.getOperands();
                for (PsiExpression operand : operands) {
                    if (containsPercentN(operand)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
