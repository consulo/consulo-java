/*
 * Copyright 2008-2013 Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class UnnecessaryToStringCallInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryTostringCallDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unnecessaryTostringCallProblemDescriptor().get();
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final String text = (String) infos[0];
        return new UnnecessaryCallToStringValueOfFix(text);
    }

    public static String calculateReplacementText(PsiExpression expression) {
        if (expression == null) {
            return "this";
        }
        return expression.getText();
    }

    private static class UnnecessaryCallToStringValueOfFix extends InspectionGadgetsFix {
        private final String replacementText;

        UnnecessaryCallToStringValueOfFix(String replacementText) {
            this.replacementText = replacementText;
        }

        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.unnecessaryCallToStringValueofQuickfix(replacementText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) descriptor.getPsiElement().getParent().getParent();
            final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                replaceExpression(methodCallExpression, "this");
            }
            else {
                methodCallExpression.replace(qualifier);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryCallToStringValueOfVisitor();
    }

    private static class UnnecessaryCallToStringValueOfVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final String referenceName = methodExpression.getReferenceName();
            if (!"toString".equals(referenceName)) {
                return;
            }
            if (isToStringCallNecessary(expression)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 0) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier != null && qualifier.getType() instanceof PsiArrayType) {
                // do not warn on nonsensical code
                return;
            }
            registerMethodCallError(expression, calculateReplacementText(qualifier));
        }

        private boolean isToStringCallNecessary(PsiMethodCallExpression expression) {
            final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
            if (parent instanceof PsiPolyadicExpression) {
                final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) parent;
                final PsiType type = polyadicExpression.getType();
                if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, type)) {
                    return true;
                }
                final PsiExpression[] operands = polyadicExpression.getOperands();
                int index = -1;
                for (int i = 0, length = operands.length; i < length; i++) {
                    final PsiExpression operand = operands[i];
                    if (expression.equals(operand)) {
                        index = i;
                    }
                }
                if (index > 0) {
                    if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, operands[index - 1].getType())) {
                        return true;
                    }
                }
                else if (operands.length > 1) {
                    if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, operands[index + 1].getType())) {
                        return true;
                    }
                }
                else {
                    return true;
                }
            }
            else if (parent instanceof PsiExpressionList) {
                final PsiExpressionList expressionList = (PsiExpressionList) parent;
                final PsiElement grandParent = expressionList.getParent();
                if (!(grandParent instanceof PsiMethodCallExpression)) {
                    return true;
                }
                final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
                final PsiReferenceExpression methodExpression1 = methodCallExpression.getMethodExpression();
                final String name = methodExpression1.getReferenceName();
                final PsiExpression[] expressions = expressionList.getExpressions();
                if ("insert".equals(name)) {
                    if (expressions.length < 2 || !expression.equals(ParenthesesUtils.stripParentheses(expressions[1]))) {
                        return true;
                    }
                    if (!isCallToMethodIn(
                        methodCallExpression,
                        CommonClassNames.JAVA_LANG_STRING_BUILDER,
                        CommonClassNames.JAVA_LANG_STRING_BUFFER
                    )) {
                        return true;
                    }

                }
                else if ("append".equals(name)) {
                    if (expressions.length < 1 || !expression.equals(ParenthesesUtils.stripParentheses(expressions[0]))) {
                        return true;
                    }
                    if (!isCallToMethodIn(
                        methodCallExpression,
                        CommonClassNames.JAVA_LANG_STRING_BUILDER,
                        CommonClassNames.JAVA_LANG_STRING_BUFFER
                    )) {
                        return true;
                    }
                }
                else if ("print".equals(name) || "println".equals(name)) {
                    if (!isCallToMethodIn(
                        methodCallExpression,
                        CommonClassNames.JAVA_IO_PRINT_STREAM,
                        CommonClassNames.JAVA_IO_PRINT_WRITER
                    )) {
                        return true;
                    }
                }
            }
            else {
                return true;
            }
            return false;
        }

        private boolean isCallToMethodIn(PsiMethodCallExpression methodCallExpression, String... classNames) {
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return false;
            }
            final String qualifiedName = containingClass.getQualifiedName();
            for (String className : classNames) {
                if (className.equals(qualifiedName)) {
                    return true;
                }
            }
            return false;
        }
    }
}
