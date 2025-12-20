/*
 * Copyright 2008-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class IfMayBeConditionalInspection extends BaseInspection {
    @SuppressWarnings("PublicField")
    public boolean reportMethodCalls = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.ifMayBeConditionalDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.ifMayBeConditionalProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.ifMayBeConditionalReportMethodCallsOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "reportMethodCalls");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new IfMayBeConditionalFix();
    }

    private static class IfMayBeConditionalFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.ifMayBeConditionalQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiIfStatement ifStatement = (PsiIfStatement) element.getParent();
            PsiStatement thenBranch = ifStatement.getThenBranch();
            PsiStatement thenStatement = ControlFlowUtils.stripBraces(thenBranch);
            PsiStatement elseBranch = ifStatement.getElseBranch();
            PsiStatement elseStatement = ControlFlowUtils.stripBraces(elseBranch);
            PsiExpression condition = ifStatement.getCondition();
            @NonNls StringBuilder replacementText = new StringBuilder();
            if (thenStatement instanceof PsiReturnStatement) {
                PsiReturnStatement elseReturn = (PsiReturnStatement) elseStatement;
                PsiReturnStatement thenReturn = (PsiReturnStatement) thenStatement;
                replacementText.append("return ");
                appendExpressionText(condition, replacementText);
                replacementText.append('?');
                PsiExpression thenReturnValue = thenReturn.getReturnValue();
                appendExpressionText(thenReturnValue, replacementText);
                replacementText.append(':');
                if (elseReturn != null) {
                    PsiExpression elseReturnValue = elseReturn.getReturnValue();
                    appendExpressionText(elseReturnValue, replacementText);
                }
                replacementText.append(';');
            }
            else if (thenStatement instanceof PsiExpressionStatement && elseStatement instanceof PsiExpressionStatement) {
                PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement) thenStatement;
                PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement) elseStatement;
                PsiExpression thenExpression = thenExpressionStatement.getExpression();
                PsiExpression elseExpression = elseExpressionStatement.getExpression();
                if (thenExpression instanceof PsiAssignmentExpression && elseExpression instanceof PsiAssignmentExpression) {
                    PsiAssignmentExpression thenAssignmentExpression = (PsiAssignmentExpression) thenExpression;
                    PsiExpression lhs = thenAssignmentExpression.getLExpression();
                    replacementText.append(lhs.getText());
                    PsiJavaToken token = thenAssignmentExpression.getOperationSign();
                    replacementText.append(token.getText());
                    appendExpressionText(condition, replacementText);
                    replacementText.append('?');
                    PsiExpression thenRhs = thenAssignmentExpression.getRExpression();
                    appendExpressionText(thenRhs, replacementText);
                    replacementText.append(':');
                    PsiAssignmentExpression elseAssignmentExpression = (PsiAssignmentExpression) elseExpression;
                    PsiExpression elseRhs = elseAssignmentExpression.getRExpression();
                    appendExpressionText(elseRhs, replacementText);
                    replacementText.append(';');
                }
                else if (thenExpression instanceof PsiMethodCallExpression && elseExpression instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression) thenExpression;
                    PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression) elseExpression;
                    PsiReferenceExpression thenMethodExpression = thenMethodCallExpression.getMethodExpression();
                    replacementText.append(thenMethodExpression.getText());
                    replacementText.append('(');
                    PsiExpressionList thenArgumentList = thenMethodCallExpression.getArgumentList();
                    PsiExpression[] thenArguments = thenArgumentList.getExpressions();
                    PsiExpressionList elseArgumentList = elseMethodCallExpression.getArgumentList();
                    PsiExpression[] elseArguments = elseArgumentList.getExpressions();
                    for (int i = 0, length = thenArguments.length; i < length; i++) {
                        if (i > 0) {
                            replacementText.append(',');
                        }
                        PsiExpression thenArgument = thenArguments[i];
                        PsiExpression elseArgument = elseArguments[i];
                        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenArgument, elseArgument)) {
                            replacementText.append(thenArgument.getText());
                        }
                        else {
                            appendExpressionText(condition, replacementText);
                            replacementText.append('?');
                            appendExpressionText(thenArgument, replacementText);
                            replacementText.append(':');
                            appendExpressionText(elseArgument, replacementText);
                        }
                    }
                    replacementText.append(");");
                }
                else {
                    return;
                }
            }
            replaceStatement(ifStatement, replacementText.toString());
        }

        private static void appendExpressionText(@Nullable PsiExpression expression, StringBuilder out) {
            expression = ParenthesesUtils.stripParentheses(expression);
            if (expression == null) {
                return;
            }
            String expressionText = expression.getText();
            if (ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                out.append('(');
                out.append(expressionText);
                out.append(')');
            }
            else {
                out.append(expressionText);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IfMayBeConditionalVisitor();
    }

    private class IfMayBeConditionalVisitor extends BaseInspectionVisitor {

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            PsiStatement thenBranch = statement.getThenBranch();
            PsiStatement elseBranch = statement.getElseBranch();
            PsiStatement thenStatement = ControlFlowUtils.stripBraces(thenBranch);
            if (thenStatement == null) {
                return;
            }
            PsiStatement elseStatement = ControlFlowUtils.stripBraces(elseBranch);
            if (elseStatement == null) {
                return;
            }
            if (thenStatement instanceof PsiReturnStatement) {
                if (!(elseStatement instanceof PsiReturnStatement)) {
                    return;
                }
                PsiReturnStatement thenReturnStatement = (PsiReturnStatement) thenStatement;
                PsiExpression thenReturnValue = ParenthesesUtils.stripParentheses(thenReturnStatement.getReturnValue());
                if (thenReturnValue instanceof PsiConditionalExpression) {
                    return;
                }
                PsiReturnStatement elseReturnStatement = (PsiReturnStatement) elseStatement;
                PsiExpression elseReturnValue = ParenthesesUtils.stripParentheses(elseReturnStatement.getReturnValue());
                if (elseReturnValue instanceof PsiConditionalExpression) {
                    return;
                }
                registerStatementError(statement);
            }
            else if (thenStatement instanceof PsiExpressionStatement) {
                if (!(elseStatement instanceof PsiExpressionStatement)) {
                    return;
                }
                PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement) thenStatement;
                PsiExpression thenExpression = thenExpressionStatement.getExpression();
                PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement) elseStatement;
                PsiExpression elseExpression = elseExpressionStatement.getExpression();
                if (thenExpression instanceof PsiAssignmentExpression) {
                    if (!(elseExpression instanceof PsiAssignmentExpression)) {
                        return;
                    }
                    PsiAssignmentExpression thenAssignmentExpression = (PsiAssignmentExpression) thenExpression;
                    PsiAssignmentExpression elseAssignmentExpression = (PsiAssignmentExpression) elseExpression;
                    if (!thenAssignmentExpression.getOperationTokenType().equals(elseAssignmentExpression.getOperationTokenType())) {
                        return;
                    }
                    PsiExpression thenLhs = thenAssignmentExpression.getLExpression();
                    PsiExpression elseLhs = elseAssignmentExpression.getLExpression();
                    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenLhs, elseLhs)) {
                        return;
                    }
                    PsiExpression thenRhs = ParenthesesUtils.stripParentheses(thenAssignmentExpression.getRExpression());
                    if (thenRhs instanceof PsiConditionalExpression) {
                        return;
                    }
                    PsiExpression elseRhs = ParenthesesUtils.stripParentheses(elseAssignmentExpression.getRExpression());
                    if (elseRhs instanceof PsiConditionalExpression) {
                        return;
                    }
                    registerStatementError(statement);
                }
                else if (reportMethodCalls && thenExpression instanceof PsiMethodCallExpression) {
                    if (!(elseExpression instanceof PsiMethodCallExpression)) {
                        return;
                    }
                    PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression) thenExpression;
                    PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression) elseExpression;
                    PsiReferenceExpression thenMethodExpression = thenMethodCallExpression.getMethodExpression();
                    PsiReferenceExpression elseMethodExpression = elseMethodCallExpression.getMethodExpression();
                    if (!EquivalenceChecker.getCanonicalPsiEquivalence()
                        .expressionsAreEquivalent(thenMethodExpression, elseMethodExpression)) {
                        return;
                    }
                    PsiExpressionList thenArgumentList = thenMethodCallExpression.getArgumentList();
                    PsiExpression[] thenArguments = thenArgumentList.getExpressions();
                    PsiExpressionList elseArgumentList = elseMethodCallExpression.getArgumentList();
                    PsiExpression[] elseArguments = elseArgumentList.getExpressions();
                    if (thenArguments.length != elseArguments.length) {
                        return;
                    }
                    int differences = 0;
                    for (int i = 0, length = thenArguments.length; i < length; i++) {
                        PsiExpression thenArgument = thenArguments[i];
                        PsiExpression elseArgument = elseArguments[i];
                        if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenArgument, elseArgument)) {
                            differences++;
                        }
                    }
                    if (differences == 1) {
                        registerStatementError(statement);
                    }
                }
            }
        }
    }
}
