/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ArrayEqualsInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.equalsCalledOnArrayDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.equalsCalledOnArrayProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        PsiArrayType type = (PsiArrayType) infos[0];
        if (type != null && type.getComponentType() instanceof PsiArrayType) {
            return new ArrayEqualsFix(true);
        }
        return new ArrayEqualsFix(false);
    }

    private static class ArrayEqualsFix extends InspectionGadgetsFix {
        private final boolean deepEquals;

        public ArrayEqualsFix(boolean deepEquals) {
            this.deepEquals = deepEquals;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return deepEquals
                ? InspectionGadgetsLocalize.replaceWithArraysDeepEquals()
                : InspectionGadgetsLocalize.replaceWithArraysEquals();
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiIdentifier name = (PsiIdentifier) descriptor.getPsiElement();
            PsiReferenceExpression expression = (PsiReferenceExpression) name.getParent();
            assert expression != null;
            PsiMethodCallExpression call = (PsiMethodCallExpression) expression.getParent();
            PsiExpression qualifier = expression.getQualifierExpression();
            assert qualifier != null;
            String qualifierText = qualifier.getText();
            assert call != null;
            PsiExpressionList argumentList = call.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            String argumentText = arguments[0].getText();
            StringBuilder newExpressionText = new StringBuilder();
            if (deepEquals) {
                newExpressionText.append("java.util.Arrays.deepEquals(");
            }
            else {
                newExpressionText.append("java.util.Arrays.equals(");
            }
            newExpressionText.append(qualifierText);
            newExpressionText.append(", ");
            newExpressionText.append(argumentText);
            newExpressionText.append(')');
            replaceExpressionAndShorten(call, newExpressionText.toString());
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ArrayEqualsVisitor();
    }

    private static class ArrayEqualsVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            PsiExpression argument = arguments[0];
            if (argument == null || !(argument.getType() instanceof PsiArrayType)) {
                return;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier != null && qualifier.getType() instanceof PsiArrayType arrayType) {
                registerMethodCallError(expression, arrayType);
            }
        }
    }
}
