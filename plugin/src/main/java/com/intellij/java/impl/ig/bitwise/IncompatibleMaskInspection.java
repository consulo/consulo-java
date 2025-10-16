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
package com.intellij.java.impl.ig.bitwise;

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class IncompatibleMaskInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern("[a-zA-Z_0-9.]+")
    public String getID() {
        return "IncompatibleBitwiseMaskOperation";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.incompatibleMaskOperationDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final PsiBinaryExpression binaryExpression =
            (PsiBinaryExpression) infos[0];
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        return JavaTokenType.EQEQ.equals(tokenType)
            ? InspectionGadgetsLocalize.incompatibleMaskOperationProblemDescriptorAlwaysFalse().get()
            : InspectionGadgetsLocalize.incompatibleMaskOperationProblemDescriptorAlwaysTrue().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IncompatibleMaskVisitor();
    }

    private static class IncompatibleMaskVisitor extends BaseInspectionVisitor {
        @Override
        public void visitBinaryExpression(
            @Nonnull PsiBinaryExpression expression
        ) {
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiType expressionType = expression.getType();
            if (expressionType == null) {
                return;
            }
            final PsiExpression strippedRhs = ParenthesesUtils.stripParentheses(rhs);
            if (strippedRhs == null) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression strippedLhs = ParenthesesUtils.stripParentheses(lhs);
            if (strippedLhs == null) {
                return;
            }
            if (isConstantMask(strippedLhs) && PsiUtil.isConstantExpression(strippedRhs)) {
                if (isIncompatibleMask((PsiBinaryExpression) strippedLhs, strippedRhs)) {
                    registerError(expression, expression);
                }
            }
            else if (isConstantMask(strippedRhs) && PsiUtil.isConstantExpression(strippedLhs)) {
                if (isIncompatibleMask((PsiBinaryExpression) strippedRhs, strippedLhs)) {
                    registerError(expression, expression);
                }
            }
        }

        private static boolean isIncompatibleMask(
            PsiBinaryExpression maskExpression,
            PsiExpression constantExpression
        ) {
            final IElementType tokenType =
                maskExpression.getOperationTokenType();
            final Object constantValue =
                ConstantExpressionUtil.computeCastTo(
                    constantExpression,
                    PsiType.LONG
                );
            if (constantValue == null) {
                return false;
            }
            final long constantLongValue = ((Long) constantValue).longValue();
            final PsiExpression maskRhs = maskExpression.getROperand();
            final PsiExpression maskLhs = maskExpression.getLOperand();
            final long constantMaskValue;
            if (PsiUtil.isConstantExpression(maskRhs)) {
                final Object rhsValue =
                    ConstantExpressionUtil.computeCastTo(
                        maskRhs,
                        PsiType.LONG
                    );
                if (rhsValue == null) {
                    return false; // Might indeed be the case with "null" literal
                    // whoes constant value evaluates to null. Check out (a|null) case.
                }
                constantMaskValue = ((Long) rhsValue).longValue();
            }
            else {
                final Object lhsValue =
                    ConstantExpressionUtil.computeCastTo(
                        maskLhs,
                        PsiType.LONG
                    );
                if (lhsValue == null) {
                    return false;
                }
                constantMaskValue = ((Long) lhsValue).longValue();
            }

            if (tokenType.equals(JavaTokenType.OR)) {
                if ((constantMaskValue | constantLongValue) != constantLongValue) {
                    return true;
                }
            }
            if (tokenType.equals(JavaTokenType.AND)) {
                if ((constantMaskValue | constantLongValue) != constantMaskValue) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isConstantMask(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (!(expression instanceof PsiBinaryExpression)) {
                return false;
            }
            final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
            final IElementType tokenType =
                binaryExpression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.OR) &&
                !tokenType.equals(JavaTokenType.AND)) {
                return false;
            }
            final PsiExpression rhs = binaryExpression.getROperand();
            if (PsiUtil.isConstantExpression(rhs)) {
                return true;
            }
            final PsiExpression lhs = binaryExpression.getLOperand();
            return PsiUtil.isConstantExpression(lhs);
        }
    }
}
