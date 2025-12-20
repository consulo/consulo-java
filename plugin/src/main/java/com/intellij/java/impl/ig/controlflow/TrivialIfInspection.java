/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.psiutils.BoolUtils;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class TrivialIfInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "RedundantIfStatement";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.trivialIfDisplayName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.trivialIfProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new TrivialIfFix();
    }

    private static class TrivialIfFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.constantConditionalExpressionSimplifyQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement ifKeywordElement = descriptor.getPsiElement();
            PsiIfStatement statement = (PsiIfStatement) ifKeywordElement.getParent();
            if (isSimplifiableAssignment(statement)) {
                replaceSimplifiableAssignment(statement);
            }
            else if (isSimplifiableReturn(statement)) {
                repaceSimplifiableReturn(statement);
            }
            else if (isSimplifiableImplicitReturn(statement)) {
                replaceSimplifiableImplicitReturn(statement);
            }
            else if (isSimplifiableAssignmentNegated(statement)) {
                replaceSimplifiableAssignmentNegated(statement);
            }
            else if (isSimplifiableReturnNegated(statement)) {
                repaceSimplifiableReturnNegated(statement);
            }
            else if (isSimplifiableImplicitReturnNegated(statement)) {
                replaceSimplifiableImplicitReturnNegated(statement);
            }
            else if (isSimplifiableImplicitAssignment(statement)) {
                replaceSimplifiableImplicitAssignment(statement);
            }
            else if (isSimplifiableImplicitAssignmentNegated(statement)) {
                replaceSimplifiableImplicitAssignmentNegated(statement);
            }
        }

        private static void replaceSimplifiableImplicitReturn(
            PsiIfStatement statement
        ) throws IncorrectOperationException {
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            String conditionText = condition.getText();
            PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(
                    statement,
                    PsiWhiteSpace.class
                );
            @NonNls String newStatement = "return " + conditionText + ';';
            replaceStatement(statement, newStatement);
            assert nextStatement != null;
            deleteElement(nextStatement);
        }

        private static void repaceSimplifiableReturn(PsiIfStatement statement)
            throws IncorrectOperationException {
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            String conditionText = condition.getText();
            @NonNls String newStatement = "return " + conditionText + ';';
            replaceStatement(statement, newStatement);
        }

        private static void replaceSimplifiableAssignment(
            PsiIfStatement statement
        )
            throws IncorrectOperationException {
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            String conditionText = condition.getText();
            PsiStatement thenBranch = statement.getThenBranch();
            PsiExpressionStatement assignmentStatement =
                (PsiExpressionStatement)
                    ControlFlowUtils.stripBraces(thenBranch);
            PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression) assignmentStatement.getExpression();
            PsiJavaToken operator =
                assignmentExpression.getOperationSign();
            String operand = operator.getText();
            PsiExpression lhs = assignmentExpression.getLExpression();
            String lhsText = lhs.getText();
            replaceStatement(
                statement,
                lhsText + operand + conditionText + ';'
            );
        }

        private static void replaceSimplifiableImplicitAssignment(
            PsiIfStatement statement
        ) throws IncorrectOperationException {
            PsiElement prevStatement =
                PsiTreeUtil.skipSiblingsBackward(
                    statement,
                    PsiWhiteSpace.class
                );
            if (prevStatement == null) {
                return;
            }
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            String conditionText = condition.getText();
            PsiStatement thenBranch = statement.getThenBranch();
            PsiExpressionStatement assignmentStatement =
                (PsiExpressionStatement)
                    ControlFlowUtils.stripBraces(thenBranch);
            PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression) assignmentStatement.getExpression();
            PsiJavaToken operator =
                assignmentExpression.getOperationSign();
            String operand = operator.getText();
            PsiExpression lhs = assignmentExpression.getLExpression();
            String lhsText = lhs.getText();
            replaceStatement(
                statement,
                lhsText + operand + conditionText + ';'
            );
            deleteElement(prevStatement);
        }

        private static void replaceSimplifiableImplicitAssignmentNegated(
            PsiIfStatement statement
        )
            throws IncorrectOperationException {
            PsiElement prevStatement =
                PsiTreeUtil.skipSiblingsBackward(
                    statement,
                    PsiWhiteSpace.class
                );
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            String conditionText =
                BoolUtils.getNegatedExpressionText(condition);
            PsiStatement thenBranch = statement.getThenBranch();
            PsiExpressionStatement assignmentStatement =
                (PsiExpressionStatement)
                    ControlFlowUtils.stripBraces(thenBranch);
            PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression)
                    assignmentStatement.getExpression();
            PsiJavaToken operator =
                assignmentExpression.getOperationSign();
            String operand = operator.getText();
            PsiExpression lhs = assignmentExpression.getLExpression();
            String lhsText = lhs.getText();
            replaceStatement(
                statement,
                lhsText + operand + conditionText + ';'
            );
            assert prevStatement != null;
            deleteElement(prevStatement);
        }

        private static void replaceSimplifiableImplicitReturnNegated(
            PsiIfStatement statement
        )
            throws IncorrectOperationException {
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            String conditionText =
                BoolUtils.getNegatedExpressionText(condition);
            PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(
                    statement,
                    PsiWhiteSpace.class
                );
            if (nextStatement == null) {
                return;
            }
            @NonNls String newStatement = "return " + conditionText + ';';
            replaceStatement(statement, newStatement);
            deleteElement(nextStatement);
        }

        private static void repaceSimplifiableReturnNegated(
            PsiIfStatement statement
        )
            throws IncorrectOperationException {
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            String conditionText =
                BoolUtils.getNegatedExpressionText(condition);
            @NonNls String newStatement = "return " + conditionText + ';';
            replaceStatement(statement, newStatement);
        }

        private static void replaceSimplifiableAssignmentNegated(
            PsiIfStatement statement
        )
            throws IncorrectOperationException {
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            String conditionText =
                BoolUtils.getNegatedExpressionText(condition);
            PsiStatement thenBranch = statement.getThenBranch();
            PsiExpressionStatement assignmentStatement =
                (PsiExpressionStatement)
                    ControlFlowUtils.stripBraces(thenBranch);
            PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression)
                    assignmentStatement.getExpression();
            PsiJavaToken operator =
                assignmentExpression.getOperationSign();
            String operand = operator.getText();
            PsiExpression lhs = assignmentExpression.getLExpression();
            String lhsText = lhs.getText();
            replaceStatement(
                statement,
                lhsText + operand + conditionText + ';'
            );
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TrivialIfVisitor();
    }

    private static class TrivialIfVisitor extends BaseInspectionVisitor {

        @Override
        public void visitIfStatement(@Nonnull PsiIfStatement ifStatement) {
            super.visitIfStatement(ifStatement);
            PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            if (PsiUtilCore.hasErrorElementChild(ifStatement)) {
                return;
            }
            if (isSimplifiableAssignment(ifStatement)) {
                registerStatementError(ifStatement);
                return;
            }
            if (isSimplifiableReturn(ifStatement)) {
                registerStatementError(ifStatement);
                return;
            }
            if (isSimplifiableImplicitReturn(ifStatement)) {
                registerStatementError(ifStatement);
                return;
            }
            if (isSimplifiableAssignmentNegated(ifStatement)) {
                registerStatementError(ifStatement);
                return;
            }
            if (isSimplifiableReturnNegated(ifStatement)) {
                registerStatementError(ifStatement);
                return;
            }
            if (isSimplifiableImplicitReturnNegated(ifStatement)) {
                registerStatementError(ifStatement);
                return;
            }
            if (isSimplifiableImplicitAssignment(ifStatement)) {
                registerStatementError(ifStatement);
                return;
            }
            if (isSimplifiableImplicitAssignmentNegated(ifStatement)) {
                registerStatementError(ifStatement);
            }
        }
    }

    public static boolean isSimplifiableImplicitReturn(
        PsiIfStatement ifStatement
    ) {
        if (ifStatement.getElseBranch() != null) {
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        PsiElement nextStatement =
            PsiTreeUtil.skipSiblingsForward(
                ifStatement,
                PsiWhiteSpace.class
            );
        if (!(nextStatement instanceof PsiStatement)) {
            return false;
        }

        PsiStatement elseBranch = (PsiStatement) nextStatement;
        return ConditionalUtils.isReturn(thenBranch, PsiKeyword.TRUE)
            && ConditionalUtils.isReturn(elseBranch, PsiKeyword.FALSE);
    }

    public static boolean isSimplifiableImplicitReturnNegated(
        PsiIfStatement ifStatement
    ) {
        if (ifStatement.getElseBranch() != null) {
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);

        PsiElement nextStatement =
            PsiTreeUtil.skipSiblingsForward(
                ifStatement,
                PsiWhiteSpace.class
            );
        if (!(nextStatement instanceof PsiStatement)) {
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;
        return ConditionalUtils.isReturn(thenBranch, PsiKeyword.FALSE)
            && ConditionalUtils.isReturn(elseBranch, PsiKeyword.TRUE);
    }

    public static boolean isSimplifiableReturn(PsiIfStatement ifStatement) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ControlFlowUtils.stripBraces(elseBranch);
        return ConditionalUtils.isReturn(thenBranch, PsiKeyword.TRUE)
            && ConditionalUtils.isReturn(elseBranch, PsiKeyword.FALSE);
    }

    public static boolean isSimplifiableReturnNegated(
        PsiIfStatement ifStatement
    ) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ControlFlowUtils.stripBraces(elseBranch);
        return ConditionalUtils.isReturn(thenBranch, PsiKeyword.FALSE)
            && ConditionalUtils.isReturn(elseBranch, PsiKeyword.TRUE);
    }

    public static boolean isSimplifiableAssignment(PsiIfStatement ifStatement) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ControlFlowUtils.stripBraces(elseBranch);
        if (ConditionalUtils.isAssignment(thenBranch, PsiKeyword.TRUE) &&
            ConditionalUtils.isAssignment(elseBranch, PsiKeyword.FALSE)) {
            PsiExpressionStatement thenExpressionStatement =
                (PsiExpressionStatement) thenBranch;
            PsiAssignmentExpression thenExpression =
                (PsiAssignmentExpression)
                    thenExpressionStatement.getExpression();
            PsiExpressionStatement elseExpressionStatement =
                (PsiExpressionStatement) elseBranch;
            PsiAssignmentExpression elseExpression =
                (PsiAssignmentExpression)
                    elseExpressionStatement.getExpression();
            IElementType thenTokenType = thenExpression.getOperationTokenType();
            if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
                return false;
            }
            PsiExpression thenLhs = thenExpression.getLExpression();
            PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(
                thenLhs,
                elseLhs
            );
        }
        else {
            return false;
        }
    }

    public static boolean isSimplifiableAssignmentNegated(
        PsiIfStatement ifStatement
    ) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ControlFlowUtils.stripBraces(elseBranch);
        if (ConditionalUtils.isAssignment(thenBranch, PsiKeyword.FALSE) &&
            ConditionalUtils.isAssignment(elseBranch, PsiKeyword.TRUE)) {
            PsiExpressionStatement thenExpressionStatement =
                (PsiExpressionStatement) thenBranch;
            PsiAssignmentExpression thenExpression =
                (PsiAssignmentExpression)
                    thenExpressionStatement.getExpression();
            PsiExpressionStatement elseExpressionStatement =
                (PsiExpressionStatement) elseBranch;
            PsiAssignmentExpression elseExpression =
                (PsiAssignmentExpression)
                    elseExpressionStatement.getExpression();
            IElementType thenTokenType = thenExpression.getOperationTokenType();
            if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
                return false;
            }
            PsiExpression thenLhs = thenExpression.getLExpression();
            PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(
                thenLhs,
                elseLhs
            );
        }
        else {
            return false;
        }
    }

    public static boolean isSimplifiableImplicitAssignment(
        PsiIfStatement ifStatement
    ) {
        if (ifStatement.getElseBranch() != null) {
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        PsiElement nextStatement =
            PsiTreeUtil.skipSiblingsBackward(
                ifStatement,
                PsiWhiteSpace.class
            );
        if (!(nextStatement instanceof PsiStatement)) {
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;
        elseBranch = ControlFlowUtils.stripBraces(elseBranch);
        if (ConditionalUtils.isAssignment(thenBranch, PsiKeyword.TRUE) &&
            ConditionalUtils.isAssignment(elseBranch, PsiKeyword.FALSE)) {
            PsiExpressionStatement thenExpressionStatement =
                (PsiExpressionStatement) thenBranch;
            PsiAssignmentExpression thenExpression =
                (PsiAssignmentExpression)
                    thenExpressionStatement.getExpression();
            PsiExpressionStatement elseExpressionStatement =
                (PsiExpressionStatement) elseBranch;
            PsiAssignmentExpression elseExpression =
                (PsiAssignmentExpression)
                    elseExpressionStatement.getExpression();
            IElementType thenTokenType = thenExpression.getOperationTokenType();
            if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
                return false;
            }
            PsiExpression thenLhs = thenExpression.getLExpression();
            PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(
                thenLhs,
                elseLhs
            );
        }
        else {
            return false;
        }
    }

    public static boolean isSimplifiableImplicitAssignmentNegated(
        PsiIfStatement ifStatement
    ) {
        if (ifStatement.getElseBranch() != null) {
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        PsiElement nextStatement =
            PsiTreeUtil.skipSiblingsBackward(
                ifStatement,
                PsiWhiteSpace.class
            );
        if (!(nextStatement instanceof PsiStatement)) {
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;
        elseBranch = ControlFlowUtils.stripBraces(elseBranch);
        if (ConditionalUtils.isAssignment(thenBranch, PsiKeyword.FALSE) &&
            ConditionalUtils.isAssignment(elseBranch, PsiKeyword.TRUE)) {
            PsiExpressionStatement thenExpressionStatement =
                (PsiExpressionStatement) thenBranch;
            PsiAssignmentExpression thenExpression =
                (PsiAssignmentExpression)
                    thenExpressionStatement.getExpression();
            PsiExpressionStatement elseExpressionStatement =
                (PsiExpressionStatement) elseBranch;
            PsiAssignmentExpression elseExpression =
                (PsiAssignmentExpression)
                    elseExpressionStatement.getExpression();
            IElementType thenTokenType = thenExpression.getOperationTokenType();
            if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
                return false;
            }
            PsiExpression thenLhs = thenExpression.getLExpression();
            PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(
                thenLhs,
                elseLhs
            );
        }
        else {
            return false;
        }
    }
}