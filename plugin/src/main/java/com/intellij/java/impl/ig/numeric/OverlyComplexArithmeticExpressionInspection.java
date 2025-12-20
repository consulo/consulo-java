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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.impl.ig.fixes.ExtractMethodFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.SingleIntegerFieldOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public abstract class OverlyComplexArithmeticExpressionInspection extends BaseInspection {
    private static final int TERM_LIMIT = 6;

    /**
     * @noinspection PublicField
     */
    public int m_limit = TERM_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    private static final Set<IElementType> arithmeticTokens = new HashSet<IElementType>(5);

    static {
        arithmeticTokens.add(JavaTokenType.PLUS);
        arithmeticTokens.add(JavaTokenType.MINUS);
        arithmeticTokens.add(JavaTokenType.ASTERISK);
        arithmeticTokens.add(JavaTokenType.DIV);
        arithmeticTokens.add(JavaTokenType.PERC);
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.overlyComplexArithmeticExpressionDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.overlyComplexArithmeticExpressionProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.overlyComplexArithmeticExpressionMaxNumberOption();
        return new SingleIntegerFieldOptionsPanel(message.get(), this, "m_limit");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ExtractMethodFix();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new OverlyComplexArithmeticExpressionVisitor();
    }

    private class OverlyComplexArithmeticExpressionVisitor extends BaseInspectionVisitor {
        @Override
        public void visitBinaryExpression(@Nonnull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitPrefixExpression(@Nonnull PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(PsiExpression expression) {
            if (isParentArithmetic(expression)) {
                return;
            }
            if (!isArithmetic(expression)) {
                return;
            }
            int numTerms = countTerms(expression);
            if (numTerms <= m_limit) {
                return;
            }
            registerError(expression);
        }

        private int countTerms(PsiExpression expression) {
            if (!isArithmetic(expression)) {
                return 1;
            }
            if (expression instanceof PsiBinaryExpression) {
                PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
                PsiExpression lhs = binaryExpression.getLOperand();
                PsiExpression rhs = binaryExpression.getROperand();
                return countTerms(lhs) + countTerms(rhs);
            }
            else if (expression instanceof PsiPrefixExpression) {
                PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
                PsiExpression operand = prefixExpression.getOperand();
                return countTerms(operand);
            }
            else if (expression instanceof PsiParenthesizedExpression) {
                PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
                PsiExpression contents = parenthesizedExpression.getExpression();
                return countTerms(contents);
            }
            return 1;
        }

        private boolean isParentArithmetic(PsiExpression expression) {
            PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpression)) {
                return false;
            }
            return isArithmetic((PsiExpression) parent);
        }

        private boolean isArithmetic(PsiExpression expression) {
            if (expression instanceof PsiBinaryExpression) {
                PsiType type = expression.getType();
                if (TypeUtils.isJavaLangString(type)) {
                    return false; //ignore string concatenations
                }
                PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
                return arithmeticTokens.contains(binaryExpression.getOperationTokenType());
            }
            else if (expression instanceof PsiPrefixExpression) {
                PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
                return arithmeticTokens.contains(prefixExpression.getOperationTokenType());
            }
            else if (expression instanceof PsiParenthesizedExpression) {
                PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
                PsiExpression contents = parenthesizedExpression.getExpression();
                return isArithmetic(contents);
            }
            return false;
        }
    }
}