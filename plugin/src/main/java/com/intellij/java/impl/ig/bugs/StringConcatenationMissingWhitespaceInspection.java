/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.java.impl.ig.psiutils.FormatUtils;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
public class StringConcatenationMissingWhitespaceInspection extends BaseInspection {

    @SuppressWarnings("PublicField")
    public boolean ignoreNonStringLiterals = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.stringConcatenationMissingWhitespaceDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.stringConcatenationMissingWhitespaceProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.stringConcatenationMissingWhitespaceOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreNonStringLiterals");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringConcatenationMissingWhitespaceVisitor();
    }

    private class StringConcatenationMissingWhitespaceVisitor extends BaseInspectionVisitor {

        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            IElementType tokenType = expression.getOperationTokenType();
            if (!JavaTokenType.PLUS.equals(tokenType) || !ExpressionUtils.hasStringType(expression)) {
                return;
            }
            boolean formatCall = FormatUtils.isFormatCallArgument(expression);
            PsiExpression[] operands = expression.getOperands();
            PsiExpression lhs = operands[0];
            for (int i = 1; i < operands.length; i++) {
                PsiExpression rhs = operands[i];
                if (isMissingWhitespace(lhs, rhs, formatCall)) {
                    PsiJavaToken token = expression.getTokenBeforeOperand(rhs);
                    if (token != null) {
                        registerError(token);
                    }
                }
                lhs = rhs;
            }
        }

        private boolean isMissingWhitespace(PsiExpression lhs, PsiExpression rhs, boolean formatCall) {
            @NonNls String lhsLiteral = ExpressionUtils.getLiteralString(lhs);
            if (lhsLiteral != null) {
                int length = lhsLiteral.length();
                if (length == 0) {
                    return false;
                }
                if (formatCall && lhsLiteral.endsWith("%n")) {
                    return false;
                }
                char c = lhsLiteral.charAt(length - 1);
                if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
                    return false;
                }
            }
            else if (ignoreNonStringLiterals) {
                return false;
            }
            @NonNls String rhsLiteral = ExpressionUtils.getLiteralString(rhs);
            if (rhsLiteral != null) {
                if (rhsLiteral.isEmpty()) {
                    return false;
                }
                if (formatCall && rhsLiteral.startsWith("%n")) {
                    return false;
                }
                char c = rhsLiteral.charAt(0);
                if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
                    return false;
                }
            }
            else if (ignoreNonStringLiterals) {
                return false;
            }
            return true;
        }
    }
}
