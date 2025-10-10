/*
 * Copyright 2011 Bas Leijdekkers
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
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ArrayHashCodeInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.arrayHashCodeDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.arrayHashCodeProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiArrayType type = (PsiArrayType) infos[0];
        if (type.getComponentType() instanceof PsiArrayType) {
            return new ArrayHashCodeFix(true);
        }
        return new ArrayHashCodeFix(false);
    }

    private static class ArrayHashCodeFix extends InspectionGadgetsFix {

        private final boolean deepHashCode;

        public ArrayHashCodeFix(boolean deepHashCode) {
            this.deepHashCode = deepHashCode;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return deepHashCode
                ? InspectionGadgetsLocalize.arraysDeepHashCodeQuickfix()
                : InspectionGadgetsLocalize.arraysHashCodeQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) grandParent;
            final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
            final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            @NonNls final StringBuilder newExpressionText = new StringBuilder();
            if (deepHashCode) {
                newExpressionText.append("java.util.Arrays.deepHashCode(");
            }
            else {
                newExpressionText.append("java.util.Arrays.hashCode(");
            }
            newExpressionText.append(qualifier.getText());
            newExpressionText.append(')');
            replaceExpressionAndShorten(
                methodCallExpression,
                newExpressionText.toString()
            );
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ArrayHashCodeVisitor();
    }

    private static class ArrayHashCodeVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.HASH_CODE.equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 0) {
                return;
            }
            final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiType type = qualifier.getType();
            if (!(type instanceof PsiArrayType)) {
                return;
            }
            registerMethodCallError(expression, type);
        }
    }
}
