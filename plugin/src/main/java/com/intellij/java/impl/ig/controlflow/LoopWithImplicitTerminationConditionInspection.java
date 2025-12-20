/*
 * Copyright 2007 Bas Leijdekkers
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
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class LoopWithImplicitTerminationConditionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.loopWithImplicitTerminationConditionDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return Boolean.TRUE.equals(infos[0])
            ? InspectionGadgetsLocalize.loopWithImplicitTerminationConditionDowhileProblemDescriptor().get()
            : InspectionGadgetsLocalize.loopWithImplicitTerminationConditionProblemDescriptor().get();
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new LoopWithImplicitTerminationConditionFix();
    }

    private static class LoopWithImplicitTerminationConditionFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.loopWithImplicitTerminationConditionQuickfix();
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            PsiExpression loopCondition;
            PsiStatement body;
            boolean firstStatement;
            if (parent instanceof PsiWhileStatement) {
                PsiWhileStatement whileStatement =
                    (PsiWhileStatement) parent;
                loopCondition = whileStatement.getCondition();
                body = whileStatement.getBody();
                firstStatement = true;
            }
            else if (parent instanceof PsiDoWhileStatement) {
                PsiDoWhileStatement doWhileStatement =
                    (PsiDoWhileStatement) parent;
                loopCondition = doWhileStatement.getCondition();
                body = doWhileStatement.getBody();
                firstStatement = false;
            }
            else if (parent instanceof PsiForStatement) {
                PsiForStatement forStatement = (PsiForStatement) parent;
                loopCondition = forStatement.getCondition();
                body = forStatement.getBody();
                firstStatement = true;
            }
            else {
                return;
            }
            if (loopCondition == null) {
                return;
            }
            PsiStatement statement;
            if (body instanceof PsiBlockStatement) {
                PsiBlockStatement blockStatement = (PsiBlockStatement) body;
                PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 0) {
                    return;
                }
                if (firstStatement) {
                    statement = statements[0];
                }
                else {
                    statement = statements[statements.length - 1];
                }
            }
            else {
                statement = body;
            }
            if (!(statement instanceof PsiIfStatement)) {
                return;
            }
            PsiIfStatement ifStatement = (PsiIfStatement) statement;
            PsiExpression ifCondition = ifStatement.getCondition();
            if (ifCondition == null) {
                return;
            }
            PsiStatement thenBranch = ifStatement.getThenBranch();
            PsiStatement elseBranch = ifStatement.getElseBranch();
            if (containsUnlabeledBreakStatement(thenBranch)) {
                String negatedExpressionText =
                    BoolUtils.getNegatedExpressionText(ifCondition);
                replaceExpression(loopCondition, negatedExpressionText);
                replaceStatement(ifStatement, elseBranch);
            }
            else if (containsUnlabeledBreakStatement(elseBranch)) {
                loopCondition.replace(ifCondition);
                if (thenBranch == null) {
                    ifStatement.delete();
                }
                else {
                    replaceStatement(ifStatement, thenBranch);
                }
            }
        }

        private static void replaceStatement(
            @Nonnull PsiStatement replacedStatement,
            @Nullable PsiStatement replacingStatement
        )
            throws IncorrectOperationException {
            if (replacingStatement == null) {
                replacedStatement.delete();
                return;
            }
            if (!(replacingStatement instanceof PsiBlockStatement)) {
                replacedStatement.replace(replacingStatement);
                return;
            }
            PsiBlockStatement blockStatement =
                (PsiBlockStatement) replacingStatement;
            PsiCodeBlock codeBlock =
                blockStatement.getCodeBlock();
            PsiElement[] children = codeBlock.getChildren();
            if (children.length > 2) {
                PsiElement receiver = replacedStatement.getParent();
                for (int i = children.length - 2; i > 0; i--) {
                    PsiElement child = children[i];
                    if (child instanceof PsiWhiteSpace) {
                        continue;
                    }
                    receiver.addAfter(child, replacedStatement);
                }
                replacedStatement.delete();
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoopWithImplicitTerminationConditionVisitor();
    }

    private static class LoopWithImplicitTerminationConditionVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            PsiExpression condition = statement.getCondition();
            if (!BoolUtils.isTrue(condition)) {
                return;
            }
            if (isLoopWithImplicitTerminationCondition(statement, true)) {
                return;
            }
            registerStatementError(statement, Boolean.FALSE);
        }

        @Override
        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            PsiExpression condition = statement.getCondition();
            if (!BoolUtils.isTrue(condition)) {
                return;
            }
            if (isLoopWithImplicitTerminationCondition(statement, false)) {
                return;
            }
            registerStatementError(statement, Boolean.TRUE);
        }

        @Override
        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            PsiExpression condition = statement.getCondition();
            if (!BoolUtils.isTrue(condition)) {
                return;
            }
            if (isLoopWithImplicitTerminationCondition(statement, true)) {
                return;
            }
            registerStatementError(statement, Boolean.FALSE);
        }

        private static boolean isLoopWithImplicitTerminationCondition(
            PsiLoopStatement statement, boolean firstStatement
        ) {
            PsiStatement body = statement.getBody();
            PsiStatement bodyStatement;
            if (body instanceof PsiBlockStatement) {
                PsiBlockStatement blockStatement =
                    (PsiBlockStatement) body;
                PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 0) {
                    return true;
                }
                if (firstStatement) {
                    bodyStatement = statements[0];
                }
                else {
                    bodyStatement = statements[statements.length - 1];
                }
            }
            else {
                bodyStatement = body;
            }
            return !isImplicitTerminationCondition(bodyStatement);
        }

        private static boolean isImplicitTerminationCondition(
            @Nullable PsiStatement statement
        ) {
            if (!(statement instanceof PsiIfStatement)) {
                return false;
            }
            PsiIfStatement ifStatement = (PsiIfStatement) statement;
            PsiStatement thenBranch = ifStatement.getThenBranch();
            if (containsUnlabeledBreakStatement(thenBranch)) {
                return true;
            }
            PsiStatement elseBranch = ifStatement.getElseBranch();
            return containsUnlabeledBreakStatement(elseBranch);
        }
    }

    static boolean containsUnlabeledBreakStatement(
        @Nullable PsiStatement statement
    ) {
        if (!(statement instanceof PsiBlockStatement)) {
            return isUnlabeledBreakStatement(statement);
        }
        PsiBlockStatement blockStatement =
            (PsiBlockStatement) statement;
        PsiCodeBlock codeBlock =
            blockStatement.getCodeBlock();
        PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length != 1) {
            return false;
        }
        PsiStatement firstStatement = statements[0];
        return isUnlabeledBreakStatement(firstStatement);
    }

    private static boolean isUnlabeledBreakStatement(
        @Nullable PsiStatement statement
    ) {
        if (!(statement instanceof PsiBreakStatement)) {
            return false;
        }
        PsiBreakStatement breakStatement =
            (PsiBreakStatement) statement;
        PsiIdentifier identifier =
            breakStatement.getLabelIdentifier();
        return identifier == null;
    }
}