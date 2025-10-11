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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
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
public class TrivialStringConcatenationInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getID() {
        return "ConcatenationWithEmptyString";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.trivialStringConcatenationDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.trivialStringConcatenationProblemDescriptor().get();
    }

    @NonNls
    static String calculateReplacementExpression(PsiLiteralExpression expression) {
        final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
        if (!(parent instanceof PsiPolyadicExpression)) {
            return null;
        }
        if (parent instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) parent;
            final PsiExpression lOperand = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
            final PsiExpression rOperand = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
            final PsiExpression replacement;
            if (ExpressionUtils.isEmptyStringLiteral(lOperand)) {
                replacement = rOperand;
            }
            else {
                replacement = lOperand;
            }
            return replacement == null ? "" : buildReplacement(replacement, false);
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) parent;
        final PsiExpression[] operands = polyadicExpression.getOperands();
        final PsiClassType stringType = TypeUtils.getStringType(expression);
        boolean seenString = false;
        boolean seenEmpty = false;
        boolean replaced = false;
        PsiExpression operandToReplace = null;
        final StringBuilder text = new StringBuilder();
        for (PsiExpression operand : operands) {
            if (operandToReplace != null && !replaced) {
                if (ExpressionUtils.hasStringType(operand)) {
                    seenString = true;
                }
                if (text.length() > 0) {
                    text.append(" + ");
                }
                text.append(buildReplacement(operandToReplace, seenString));
                text.append(" + ");
                text.append(operand.getText());
                replaced = true;
                continue;
            }
            if (operand == expression) {
                seenEmpty = true;
                continue;
            }
            if (seenEmpty && !replaced) {
                operandToReplace = operand;
                continue;
            }
            if (stringType.equals(operand.getType())) {
                seenString = true;
            }
            if (text.length() > 0) {
                text.append(" + ");
            }
            text.append(operand.getText());
        }
        if (!replaced && operandToReplace != null) {
            text.append(" + ");
            text.append(buildReplacement(operandToReplace, seenString));
        }
        return text.toString();
    }

    static String buildReplacement(@Nonnull PsiExpression operandToReplace, boolean seenString) {
        if (ExpressionUtils.isNullLiteral(operandToReplace)) {
            if (seenString) {
                return "null";
            }
            else {
                return "String.valueOf((Object)null)";
            }
        }
        if (seenString || ExpressionUtils.hasStringType(operandToReplace)) {
            return operandToReplace.getText();
        }
        return "String.valueOf(" + operandToReplace.getText() + ')';
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryTemporaryObjectFix((PsiLiteralExpression) infos[0]);
    }

    private static class UnnecessaryTemporaryObjectFix extends InspectionGadgetsFix {
        private final LocalizeValue myName;

        private UnnecessaryTemporaryObjectFix(PsiLiteralExpression expression) {
            myName = InspectionGadgetsLocalize.stringReplaceQuickfix(calculateReplacementExpression(expression));
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return myName;
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiLiteralExpression expression = (PsiLiteralExpression) descriptor.getPsiElement();
            final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
            if (!(parent instanceof PsiExpression)) {
                return;
            }
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression((PsiExpression) parent, newExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TrivialStringConcatenationVisitor();
    }

    private static class TrivialStringConcatenationVisitor extends BaseInspectionVisitor {
        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            if (!ExpressionUtils.hasStringType(expression)) {
                return;
            }
            final PsiExpression[] operands = expression.getOperands();
            for (PsiExpression operand : operands) {
                operand = ParenthesesUtils.stripParentheses(operand);
                if (operand == null) {
                    return;
                }
                if (!ExpressionUtils.isEmptyStringLiteral(operand)) {
                    continue;
                }
                if (PsiUtil.isConstantExpression(expression)) {
                    return;
                }
                registerError(operand, operand);
            }
        }
    }
}