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
package com.intellij.java.impl.ig.controlflow;

import java.util.Collections;

import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiBlockStatement;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiLoopStatement;
import com.intellij.java.language.psi.PsiReturnStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.analysis.impl.refactoring.extractMethod.InputVariables;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.Match;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class IfStatementWithIdenticalBranchesInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.ifStatementWithIdenticalBranchesDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.ifStatementWithIdenticalBranchesProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new CollapseIfFix();
    }

    private static class CollapseIfFix extends InspectionGadgetsFix {

        public CollapseIfFix() {
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.ifStatementWithIdenticalBranchesCollapseQuickfix();
        }

        @Override
        public void doFix(@Nonnull Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement identifier = descriptor.getPsiElement();
            PsiIfStatement statement = (PsiIfStatement) identifier.getParent();
            assert statement != null;
            PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                // implicit else branch after the if
                statement.delete();
                return;
            }
            if (elseBranch instanceof PsiIfStatement) {
                PsiIfStatement elseIfStatement = (PsiIfStatement) elseBranch;
                PsiExpression condition1 = statement.getCondition();
                PsiExpression condition2 = elseIfStatement.getCondition();
                if (condition1 == null) {
                    return;
                }
                replaceExpression(condition1, buildOrExpressionText(condition1, condition2));
                PsiStatement elseElseBranch = elseIfStatement.getElseBranch();
                if (elseElseBranch == null) {
                    elseIfStatement.delete();
                }
                else {
                    elseIfStatement.replace(elseElseBranch);
                }
            }
            else {
                PsiElement parent = statement.getParent();
                if (thenBranch instanceof PsiBlockStatement) {
                    PsiBlockStatement blockStatement = (PsiBlockStatement) thenBranch;
                    if (parent instanceof PsiCodeBlock) {
                        PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                        PsiStatement[] statements =
                            codeBlock.getStatements();
                        if (statements.length > 0) {
                            parent.addRangeBefore(statements[0], statements[statements.length - 1], statement);
                        }
                        statement.delete();
                    }
                    else {
                        statement.replace(blockStatement);
                    }
                }
                else {
                    statement.replace(thenBranch);
                }
            }
        }

        private static String buildOrExpressionText(
            PsiExpression expression1,
            PsiExpression expression2
        ) {
            StringBuilder result = new StringBuilder();
            if (expression1 != null) {
                result.append(expression1.getText());
            }
            result.append("||");
            if (expression2 != null) {
                result.append(expression2.getText());
            }
            return result.toString();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IfStatementWithIdenticalBranchesVisitor();
    }

    private static class IfStatementWithIdenticalBranchesVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitIfStatement(
            @Nonnull PsiIfStatement ifStatement
        ) {
            super.visitIfStatement(ifStatement);
            PsiStatement elseBranch = ifStatement.getElseBranch();
            PsiStatement thenBranch = ifStatement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            Project project = ifStatement.getProject();
            InputVariables inputVariables =
                new InputVariables(Collections.<PsiVariable>emptyList(),
                    project, new LocalSearchScope(thenBranch), false
                );
            DuplicatesFinder finder =
                new DuplicatesFinder(new PsiElement[]{thenBranch},
                    inputVariables, null,
                    Collections.<PsiVariable>emptyList()
                );
            if (elseBranch instanceof PsiIfStatement) {
                PsiIfStatement statement =
                    (PsiIfStatement) elseBranch;
                PsiStatement branch = statement.getThenBranch();
                if (branch == null) {
                    return;
                }
                Match match = finder.isDuplicate(branch, true);
                if (match != null && match.getReturnValue() == null) {
                    registerStatementError(ifStatement, statement);
                    return;
                }
            }
            if (elseBranch == null) {
                checkIfStatementWithoutElseBranch(ifStatement);
            }
            else {
                Match match = finder.isDuplicate(elseBranch, true);
                if (match != null) {
                    registerStatementError(ifStatement);
                }
            }
        }

        private void checkIfStatementWithoutElseBranch(
            PsiIfStatement ifStatement
        ) {
            PsiStatement thenBranch = ifStatement.getThenBranch();
            if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
                return;
            }
            PsiStatement nextStatement = getNextStatement(ifStatement);
            if (thenBranch instanceof PsiBlockStatement) {
                PsiBlockStatement blockStatement =
                    (PsiBlockStatement) thenBranch;
                PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                PsiStatement[] statements = codeBlock.getStatements();
                PsiStatement lastStatement =
                    statements[statements.length - 1];
                for (PsiStatement statement : statements) {
                    if (nextStatement == null) {
                        if (statement == lastStatement &&
                            statement instanceof PsiReturnStatement) {
                            PsiReturnStatement returnStatement =
                                (PsiReturnStatement) statement;
                            if (returnStatement.getReturnValue() == null) {
                                registerStatementError(ifStatement);
                            }
                        }
                        return;
                    }
                    else if (!EquivalenceChecker.getCanonicalPsiEquivalence().statementsAreEquivalent(
                        statement, nextStatement)) {
                        return;
                    }
                    nextStatement = getNextStatement(nextStatement);
                }
            }
            else if (!EquivalenceChecker.getCanonicalPsiEquivalence().statementsAreEquivalent(
                thenBranch, nextStatement)) {
                return;
            }
            registerStatementError(ifStatement);
        }

        @Nullable
        private static PsiStatement getNextStatement(PsiStatement statement) {
            PsiStatement nextStatement =
                PsiTreeUtil.getNextSiblingOfType(
                    statement,
                    PsiStatement.class
                );
            while (nextStatement == null) {
                //noinspection AssignmentToMethodParameter
                statement = PsiTreeUtil.getParentOfType(
                    statement,
                    PsiStatement.class
                );
                if (statement == null) {
                    return null;
                }
                if (statement instanceof PsiLoopStatement) {
                    // return in a loop statement is not the same as continuing
                    // looping.
                    return statement;
                }
                nextStatement = PsiTreeUtil.getNextSiblingOfType(
                    statement,
                    PsiStatement.class
                );
                if (nextStatement == null) {
                    continue;
                }
                PsiElement statementParent = statement.getParent();
                if (!(statementParent instanceof PsiIfStatement)) {
                    continue;
                }
                // nextStatement should not be the else part of an if statement
                PsiElement nextStatementParent =
                    nextStatement.getParent();
                if (statementParent.equals(nextStatementParent)) {
                    nextStatement = null;
                }
            }
            return nextStatement;
        }
    }
}
