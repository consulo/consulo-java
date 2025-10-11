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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
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
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class MisorderedAssertEqualsParametersInspection extends BaseInspection {

    @Nullable
    @Override
    public String getAlternativeID() {
        return "MisorderedAssertEqualsArguments";
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.misorderedAssertEqualsParametersDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.misorderedAssertEqualsParametersProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new FlipParametersFix();
    }

    private static class FlipParametersFix extends InspectionGadgetsFix {

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.misorderedAssertEqualsParametersFlipQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiElement parent = methodNameIdentifier.getParent();
            assert parent != null;
            final PsiMethodCallExpression callExpression = (PsiMethodCallExpression) parent.getParent();
            assert callExpression != null;
            final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            assert method != null;
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiType stringType = TypeUtils.getStringType(callExpression);
            final PsiType parameterType1 = parameters[0].getType();
            final PsiExpressionList argumentList = callExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            final PsiExpression actualArgument;
            final PsiExpression expectedArgument;
            if (parameterType1.equals(stringType) && parameters.length > 2) {
                expectedArgument = arguments[1];
                actualArgument = arguments[2];
            }
            else {
                expectedArgument = arguments[0];
                actualArgument = arguments[1];
            }
            final String actualArgumentText = actualArgument.getText();
            final String expectedArgumentText = expectedArgument.getText();
            replaceExpression(expectedArgument, actualArgumentText);
            replaceExpression(actualArgument, expectedArgumentText);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MisorderedAssertEqualsParametersVisitor();
    }

    private static class MisorderedAssertEqualsParametersVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            @NonNls final String methodName = methodExpression.getReferenceName();
            if (!"assertEquals".equals(methodName) && !"assertArrayEquals".equals(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
                !InheritanceUtil.isInheritor(containingClass, "org.junit.Assert")) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length < 2) {
                return;
            }
            final PsiType stringType = TypeUtils.getStringType(expression);
            final PsiType argumentType1 = arguments[0].getType();
            final PsiExpression expectedArgument;
            final PsiExpression actualArgument;
            if (stringType.equals(argumentType1) && arguments.length > 2) {
                expectedArgument = arguments[1];
                actualArgument = arguments[2];
            }
            else {
                expectedArgument = arguments[0];
                actualArgument = arguments[1];
            }
            if (expectedArgument == null || actualArgument == null) {
                return;
            }
            if (isLiteralOrConstant(expectedArgument)) {
                return;
            }
            if (!isLiteralOrConstant(actualArgument)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isLiteralOrConstant(PsiExpression expression) {
            if (expression instanceof PsiLiteralExpression) {
                return true;
            }
            else if (expression instanceof PsiNewExpression) {
                final PsiNewExpression newExpression = (PsiNewExpression) expression;
                final PsiExpressionList argumentList = newExpression.getArgumentList();
                if (argumentList == null) {
                    return true;
                }
                for (PsiExpression argument : argumentList.getExpressions()) {
                    if (!isLiteralOrConstant(argument)) {
                        return false;
                    }
                }
                return true;
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiField)) {
                return false;
            }
            final PsiField field = (PsiField) target;
            return field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL);
        }
    }
}