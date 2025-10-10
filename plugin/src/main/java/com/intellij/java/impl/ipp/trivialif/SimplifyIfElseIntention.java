/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.trivialif;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ConditionalUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SimplifyIfElseIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class SimplifyIfElseIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.simplifyIfElseIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new SimplifyIfElsePredicate();
    }

    public void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        final PsiIfStatement statement = (PsiIfStatement) element.getParent();
        if (SimplifyIfElsePredicate.isSimplifiableAssignment(statement)) {
            replaceSimplifiableAssignment(statement);
        }
        else if (SimplifyIfElsePredicate.isSimplifiableReturn(statement)) {
            replaceSimplifiableReturn(statement);
        }
        else if (SimplifyIfElsePredicate.isSimplifiableImplicitReturn(
            statement)) {
            replaceSimplifiableImplicitReturn(statement);
        }
        else if (SimplifyIfElsePredicate.isSimplifiableAssignmentNegated(
            statement)) {
            replaceSimplifiableAssignmentNegated(statement);
        }
        else if (SimplifyIfElsePredicate.isSimplifiableReturnNegated(
            statement)) {
            replaceSimplifiableReturnNegated(statement);
        }
        else if (SimplifyIfElsePredicate.isSimplifiableImplicitReturnNegated(
            statement)) {
            replaceSimplifiableImplicitReturnNegated(statement);
        }
        else if (SimplifyIfElsePredicate.isSimplifiableImplicitAssignment(
            statement)) {
            replaceSimplifiableImplicitAssignment(statement);
        }
        else if (
            SimplifyIfElsePredicate.isSimplifiableImplicitAssignmentNegated(
                statement)) {
            replaceSimplifiableImplicitAssignmentNegated(statement);
        }
    }

    private static void replaceSimplifiableImplicitReturn(
        PsiIfStatement statement)
        throws IncorrectOperationException {
        final PsiExpression condition = statement.getCondition();
        if (condition == null) {
            return;
        }
        final String conditionText = condition.getText();
        final PsiElement nextStatement =
            PsiTreeUtil.skipSiblingsForward(statement,
                PsiWhiteSpace.class);
        @NonNls final String newStatement = "return " + conditionText + ';';
        replaceStatement(newStatement, statement);
        assert nextStatement != null;
        nextStatement.delete();
    }

    private static void replaceSimplifiableReturn(PsiIfStatement statement)
        throws IncorrectOperationException {
        final PsiExpression condition = statement.getCondition();
        if (condition == null) {
            return;
        }
        final String conditionText = condition.getText();
        @NonNls final String newStatement = "return " + conditionText + ';';
        replaceStatement(newStatement, statement);
    }

    private static void replaceSimplifiableAssignment(PsiIfStatement statement)
        throws IncorrectOperationException {
        final PsiExpression condition = statement.getCondition();
        if (condition == null) {
            return;
        }
        final String conditionText = condition.getText();
        final PsiStatement thenBranch = statement.getThenBranch();
        final PsiExpressionStatement assignmentStatement =
            (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
        final PsiAssignmentExpression assignmentExpression =
            (PsiAssignmentExpression) assignmentStatement.getExpression();
        final PsiJavaToken operator = assignmentExpression.getOperationSign();
        final String operand = operator.getText();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final String lhsText = lhs.getText();
        replaceStatement(lhsText + operand + conditionText + ';',
            statement);
    }

    private static void replaceSimplifiableImplicitAssignment(
        PsiIfStatement statement)
        throws IncorrectOperationException {
        final PsiElement prevStatement =
            PsiTreeUtil.skipSiblingsBackward(statement,
                PsiWhiteSpace.class);
        final PsiExpression condition = statement.getCondition();
        if (condition == null) {
            return;
        }
        final String conditionText = condition.getText();
        final PsiStatement thenBranch = statement.getThenBranch();
        final PsiExpressionStatement assignmentStatement =
            (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
        final PsiAssignmentExpression assignmentExpression =
            (PsiAssignmentExpression) assignmentStatement.getExpression();
        final PsiJavaToken operator = assignmentExpression.getOperationSign();
        final String operand = operator.getText();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final String lhsText = lhs.getText();
        replaceStatement(lhsText + operand + conditionText + ';',
            statement);
        assert prevStatement != null;
        prevStatement.delete();
    }

    private static void replaceSimplifiableImplicitAssignmentNegated(
        PsiIfStatement statement)
        throws IncorrectOperationException {
        final PsiElement prevStatement =
            PsiTreeUtil.skipSiblingsBackward(statement,
                PsiWhiteSpace.class);
        final PsiExpression condition = statement.getCondition();
        final String conditionText =
            BoolUtils.getNegatedExpressionText(condition);
        final PsiStatement thenBranch = statement.getThenBranch();
        final PsiExpressionStatement assignmentStatement =
            (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
        final PsiAssignmentExpression assignmentExpression =
            (PsiAssignmentExpression) assignmentStatement.getExpression();
        final PsiJavaToken operator = assignmentExpression.getOperationSign();
        final String operand = operator.getText();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final String lhsText = lhs.getText();
        replaceStatement(lhsText + operand + conditionText + ';',
            statement);
        assert prevStatement != null;
        prevStatement.delete();
    }

    private static void replaceSimplifiableImplicitReturnNegated(
        PsiIfStatement statement)
        throws IncorrectOperationException {
        final PsiExpression condition = statement.getCondition();
        final String conditionText =
            BoolUtils.getNegatedExpressionText(condition);
        final PsiElement nextStatement =
            PsiTreeUtil.skipSiblingsForward(statement,
                PsiWhiteSpace.class);
        @NonNls final String newStatement = "return " + conditionText + ';';
        replaceStatement(newStatement, statement);
        assert nextStatement != null;
        nextStatement.delete();
    }

    private static void replaceSimplifiableReturnNegated(
        PsiIfStatement statement)
        throws IncorrectOperationException {
        final PsiExpression condition = statement.getCondition();
        final String conditionText =
            BoolUtils.getNegatedExpressionText(condition);
        @NonNls final String newStatement = "return " + conditionText + ';';
        replaceStatement(newStatement, statement);
    }

    private static void replaceSimplifiableAssignmentNegated(
        PsiIfStatement statement)
        throws IncorrectOperationException {
        final PsiExpression condition = statement.getCondition();
        final String conditionText =
            BoolUtils.getNegatedExpressionText(condition);
        final PsiStatement thenBranch = statement.getThenBranch();
        final PsiExpressionStatement assignmentStatement =
            (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
        final PsiAssignmentExpression assignmentExpression =
            (PsiAssignmentExpression) assignmentStatement.getExpression();
        final PsiJavaToken operator = assignmentExpression.getOperationSign();
        final String operand = operator.getText();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final String lhsText = lhs.getText();
        replaceStatement(lhsText + operand + conditionText + ';',
            statement);
    }
}