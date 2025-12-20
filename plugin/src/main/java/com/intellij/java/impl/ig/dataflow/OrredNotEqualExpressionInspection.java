/*
 * Copyright 2009 Bas Leijdekkers
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
package com.intellij.java.impl.ig.dataflow;

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
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class OrredNotEqualExpressionInspection extends BaseInspection {

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.orredNotEqualExpressionDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.orredNotEqualExpressionProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new OrredNotEqualExpressionFix();
    }

    private static class OrredNotEqualExpressionFix
        extends InspectionGadgetsFix {

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.orredNotEqualExpressionQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiBinaryExpression)) {
                return;
            }
            PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) element;
            PsiExpression lhs = binaryExpression.getLOperand();
            String lhsText = lhs.getText();
            PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return;
            }
            String rhsText = rhs.getText();
            replaceExpression(binaryExpression, lhsText + "&&" + rhsText);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new OrredNotEqualExpressionVisitor();
    }

    private static class OrredNotEqualExpressionVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitBinaryExpression(PsiBinaryExpression expression) {
            IElementType tokenType = expression.getOperationTokenType();
            if (JavaTokenType.OROR != tokenType) {
                return;
            }
            PsiExpression lhs = expression.getLOperand();
            PsiExpression rhs = expression.getROperand();
            Pair<PsiReferenceExpression, PsiExpression> pair1 =
                getReferenceExpressionPair(lhs);
            Pair<PsiReferenceExpression, PsiExpression> pair2 =
                getReferenceExpressionPair(rhs);
            if (pair1 == null || pair2 == null) {
                return;
            }
            PsiExpression expression1 = pair1.getSecond();
            PsiExpression expression2 = pair2.getSecond();
            if (expression1 == null || expression2 == null) {
                return;
            }
            Project project = expression1.getProject();
            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            PsiConstantEvaluationHelper constantEvaluationHelper =
                psiFacade.getConstantEvaluationHelper();
            Object constant1 =
                constantEvaluationHelper.computeConstantExpression(expression1);
            Object constant2 =
                constantEvaluationHelper.computeConstantExpression(expression2);
            if (constant1 == null || constant2 == null || constant1 == constant2) {
                return;
            }
            PsiReferenceExpression referenceExpression1 = pair1.getFirst();
            PsiReferenceExpression referenceExpression2 = pair2.getFirst();
            if (referenceExpression1.resolve() == referenceExpression2.resolve()) {
                registerError(expression);
            }
        }

        @Nullable
        private static Pair<PsiReferenceExpression, PsiExpression>
        getReferenceExpressionPair(PsiExpression expression) {
            if (!(expression instanceof PsiBinaryExpression)) {
                return null;
            }
            PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
            IElementType tokenType =
                binaryExpression.getOperationTokenType();
            if (JavaTokenType.NE != tokenType) {
                return null;
            }
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            if (lhs instanceof PsiReferenceExpression) {
                PsiReferenceExpression lref =
                    (PsiReferenceExpression) lhs;
                return new Pair(lref, rhs);
            }
            else if (rhs instanceof PsiReferenceExpression) {
                PsiReferenceExpression rref =
                    (PsiReferenceExpression) rhs;
                return new Pair(rref, lhs);
            }
            return null;
        }
    }
}
