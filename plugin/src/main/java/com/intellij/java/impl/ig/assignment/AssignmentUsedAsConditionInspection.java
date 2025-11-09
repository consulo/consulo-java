/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.impl.ig.psiutils.WellFormednessUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class AssignmentUsedAsConditionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.assignmentUsedAsConditionDisplayName();
    }

    @Nonnull
    @Override
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.assignmentUsedAsConditionProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new AssignmentUsedAsConditionFix();
    }

    private static class AssignmentUsedAsConditionFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.assignmentUsedAsConditionReplaceQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiAssignmentExpression expression = (PsiAssignmentExpression) descriptor.getPsiElement();
            PsiExpression leftExpression = expression.getLExpression();
            PsiExpression rightExpression = expression.getRExpression();
            assert rightExpression != null;
            String newExpression = leftExpression.getText() + "==" + rightExpression.getText();
            replaceExpression(expression, newExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentUsedAsConditionVisitor();
    }

    private static class AssignmentUsedAsConditionVisitor extends BaseInspectionVisitor {
        @Override
        public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if (!WellFormednessUtils.isWellFormed(expression)) {
                return;
            }
            PsiElement parent = expression.getParent();
            if (parent == null) {
                return;
            }
            if (parent instanceof PsiIfStatement ifStatement) {
                checkIfStatementCondition(ifStatement, expression);
            }
            else if (parent instanceof PsiWhileStatement whileStatement) {
                checkWhileStatementCondition(whileStatement, expression);
            }
            else if (parent instanceof PsiForStatement forStatement) {
                checkForStatementCondition(forStatement, expression);
            }
            else if (parent instanceof PsiDoWhileStatement doWhileStatement) {
                checkDoWhileStatementCondition(doWhileStatement, expression);
            }
        }

        private void checkIfStatementCondition(PsiIfStatement ifStatement, PsiAssignmentExpression expression) {
            if (expression.equals(ifStatement.getCondition())) {
                registerError(expression);
            }
        }

        private void checkDoWhileStatementCondition(PsiDoWhileStatement doWhileStatement, PsiAssignmentExpression expression) {
            if (expression.equals(doWhileStatement.getCondition())) {
                registerError(expression);
            }
        }

        private void checkForStatementCondition(PsiForStatement forStatement, PsiAssignmentExpression expression) {
            if (expression.equals(forStatement.getCondition())) {
                registerError(expression);
            }
        }

        private void checkWhileStatementCondition(PsiWhileStatement whileStatement, PsiAssignmentExpression expression) {
            if (expression.equals(whileStatement.getCondition())) {
                registerError(expression);
            }
        }
    }
}