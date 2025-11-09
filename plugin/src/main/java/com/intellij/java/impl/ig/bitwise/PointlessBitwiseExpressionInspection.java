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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.Set;

@ExtensionImpl
public class PointlessBitwiseExpressionInspection extends BaseInspection {
    static final Set<IElementType> BITWISE_TOKENS = Set.of(
        JavaTokenType.AND,
        JavaTokenType.OR,
        JavaTokenType.XOR,
        JavaTokenType.LTLT,
        JavaTokenType.GTGT,
        JavaTokenType.GTGTGT
    );
    static final Set<IElementType> BITWISE_LOGICAL_TOKENS = Set.of(JavaTokenType.AND, JavaTokenType.OR, JavaTokenType.XOR);
    static final Set<IElementType> BITWISE_SHIFT_TOKENS = Set.of(JavaTokenType.LTLT, JavaTokenType.GTGT, JavaTokenType.GTGTGT);

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreExpressionsContainingConstants = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.pointlessBitwiseExpressionDisplayName();
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String buildErrorString(Object... infos) {
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) infos[0];
        String replacementExpression = calculateReplacementExpression(polyadicExpression);
        return InspectionGadgetsLocalize.expressionCanBeReplacedProblemDescriptor(replacementExpression).get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
            InspectionGadgetsLocalize.pointlessBitwiseExpressionIgnoreOption().get(),
            this, "m_ignoreExpressionsContainingConstants"
        );
    }

    @RequiredReadAction
    String calculateReplacementExpression(PsiPolyadicExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        PsiExpression[] operands = expression.getOperands();
        if (tokenType.equals(JavaTokenType.AND)) {
            for (PsiExpression operand : operands) {
                if (isZero(operand)) {
                    return operand.getText();
                }
                else if (isAllOnes(operand)) {
                    return getText(expression, operand);
                }
            }
        }
        else if (tokenType.equals(JavaTokenType.OR)) {
            for (PsiExpression operand : operands) {
                if (isZero(operand)) {
                    return getText(expression, operand);
                }
                else if (isAllOnes(operand)) {
                    return operand.getText();
                }
            }
        }
        else if (tokenType.equals(JavaTokenType.XOR)) {
            for (PsiExpression operand : operands) {
                if (isAllOnes(operand)) {
                    return '~' + getText(expression, operand);
                }
                else if (isZero(operand)) {
                    return getText(expression, operand);
                }
            }
        }
        else if (BITWISE_SHIFT_TOKENS.contains(tokenType)) {
            for (PsiExpression operand : operands) {
                if (isZero(operand)) {
                    return getText(expression, operand);
                }
            }
        }
        return "";
    }

    @RequiredReadAction
    private static String getText(PsiPolyadicExpression expression, PsiExpression exclude) {
        PsiExpression[] operands = expression.getOperands();
        boolean addToken = false;
        StringBuilder text = new StringBuilder();
        for (PsiExpression operand : operands) {
            if (operand == exclude) {
                continue;
            }
            if (addToken) {
                PsiJavaToken token = expression.getTokenBeforeOperand(operand);
                text.append(' ');
                if (token != null) {
                    text.append(token.getText());
                    text.append(' ');
                }
            }
            else {
                addToken = true;
            }
            text.append(operand.getText());
        }
        return text.toString();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PointlessBitwiseVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new PointlessBitwiseFix();
    }

    private class PointlessBitwiseFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.pointlessBitwiseExpressionSimplifyQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiPolyadicExpression expression = (PsiPolyadicExpression) descriptor.getPsiElement();
            String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private class PointlessBitwiseVisitor extends BaseInspectionVisitor {
        @Override
        @SuppressWarnings("SimplifiableIfStatement")
        public void visitPolyadicExpression(@Nonnull PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            IElementType sign = expression.getOperationTokenType();
            if (!BITWISE_TOKENS.contains(sign)) {
                return;
            }
            PsiExpression[] operands = expression.getOperands();
            for (PsiExpression operand : operands) {
                if (operand == null) {
                    return;
                }
                PsiType type = operand.getType();
                if (type == null || type.equals(PsiType.BOOLEAN) || type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
                    return;
                }
            }
            boolean isPointless;
            if (BITWISE_LOGICAL_TOKENS.contains(sign)) {
                isPointless = booleanExpressionIsPointless(operands);
            }
            else if (BITWISE_SHIFT_TOKENS.contains(sign)) {
                isPointless = shiftExpressionIsPointless(operands);
            }
            else {
                isPointless = false;
            }
            if (!isPointless) {
                return;
            }
            registerError(expression, expression);
        }

        private boolean booleanExpressionIsPointless(PsiExpression[] operands) {
            for (PsiExpression operand : operands) {
                if (isZero(operand) || isAllOnes(operand)) {
                    return true;
                }
            }
            return false;
        }

        private boolean shiftExpressionIsPointless(PsiExpression[] operands) {
            for (int i = 1; i < operands.length; i++) {
                PsiExpression operand = operands[i];
                if (isZero(operand)) {
                    return true;
                }
            }
            return false;
        }
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean isZero(PsiExpression expression) {
        if (m_ignoreExpressionsContainingConstants && !(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        return ExpressionUtils.isZero(expression);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean isAllOnes(PsiExpression expression) {
        if (m_ignoreExpressionsContainingConstants && !(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        PsiType expressionType = expression.getType();
        Object value = ConstantExpressionUtil.computeCastTo(expression, expressionType);
        if (value == null) {
            return false;
        }
        if (value instanceof Integer && (Integer) value == 0xFFFFFFFF) {
            return true;
        }
        if (value instanceof Long && (Long) value == 0xFFFFFFFFFFFFFFFFL) {
            return true;
        }
        if (value instanceof Short && (Short) value == (short) 0xFFFF) {
            return true;
        }
        if (value instanceof Character && (Character) value == (char) 0xFFFF) {
            return true;
        }
        return value instanceof Byte && (Byte) value == (byte) 0xFF;
    }
}