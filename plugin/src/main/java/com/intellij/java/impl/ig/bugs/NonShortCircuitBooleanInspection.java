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

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class NonShortCircuitBooleanInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "NonShortCircuitBooleanExpression";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.nonShortCircuitBooleanExpressionDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.nonShortCircuitBooleanExpressionProblemDescriptor().get();
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new NonShortCircuitBooleanFix();
    }

    private static class NonShortCircuitBooleanFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.nonShortCircuitBooleanExpressionReplaceQuickfix();
        }

        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiPolyadicExpression expression = (PsiPolyadicExpression) descriptor.getPsiElement();
            IElementType tokenType = expression.getOperationTokenType();
            String operandText = getShortCircuitOperand(tokenType);
            PsiExpression[] operands = expression.getOperands();
            StringBuilder newExpression = new StringBuilder();
            for (PsiExpression operand : operands) {
                if (newExpression.length() != 0) {
                    newExpression.append(operandText);
                }
                newExpression.append(operand.getText());
            }
            replaceExpression(expression, newExpression.toString());
        }

        private static String getShortCircuitOperand(IElementType tokenType) {
            if (tokenType.equals(JavaTokenType.AND)) {
                return "&&";
            }
            else {
                return "||";
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonShortCircuitBooleanVisitor();
    }

    private static class NonShortCircuitBooleanVisitor extends BaseInspectionVisitor {

        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.AND) && !tokenType.equals(JavaTokenType.OR)) {
                return;
            }
            PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!type.equals(PsiType.BOOLEAN)) {
                return;
            }
            registerError(expression);
        }
    }
}