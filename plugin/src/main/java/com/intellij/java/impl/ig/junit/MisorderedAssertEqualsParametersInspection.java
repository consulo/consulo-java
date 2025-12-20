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
            PsiElement methodNameIdentifier = descriptor.getPsiElement();
            PsiElement parent = methodNameIdentifier.getParent();
            assert parent != null;
            PsiMethodCallExpression callExpression = (PsiMethodCallExpression) parent.getParent();
            assert callExpression != null;
            PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
            PsiMethod method = (PsiMethod) methodExpression.resolve();
            assert method != null;
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            PsiType stringType = TypeUtils.getStringType(callExpression);
            PsiType parameterType1 = parameters[0].getType();
            PsiExpressionList argumentList = callExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            PsiExpression actualArgument;
            PsiExpression expectedArgument;
            if (parameterType1.equals(stringType) && parameters.length > 2) {
                expectedArgument = arguments[1];
                actualArgument = arguments[2];
            }
            else {
                expectedArgument = arguments[0];
                actualArgument = arguments[1];
            }
            String actualArgumentText = actualArgument.getText();
            String expectedArgumentText = expectedArgument.getText();
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
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            @NonNls String methodName = methodExpression.getReferenceName();
            if (!"assertEquals".equals(methodName) && !"assertArrayEquals".equals(methodName)) {
                return;
            }
            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiClass containingClass = method.getContainingClass();
            if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
                !InheritanceUtil.isInheritor(containingClass, "org.junit.Assert")) {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length < 2) {
                return;
            }
            PsiType stringType = TypeUtils.getStringType(expression);
            PsiType argumentType1 = arguments[0].getType();
            PsiExpression expectedArgument;
            PsiExpression actualArgument;
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
                PsiNewExpression newExpression = (PsiNewExpression) expression;
                PsiExpressionList argumentList = newExpression.getArgumentList();
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
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
            PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiField)) {
                return false;
            }
            PsiField field = (PsiField) target;
            return field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL);
        }
    }
}