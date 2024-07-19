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
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class AssignmentUsedAsConditionInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.assignmentUsedAsConditionDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.assignmentUsedAsConditionProblemDescriptor().get();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new AssignmentUsedAsConditionFix();
  }

  private static class AssignmentUsedAsConditionFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.assignmentUsedAsConditionReplaceQuickfix().get();
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiAssignmentExpression expression =
        (PsiAssignmentExpression)descriptor.getPsiElement();
      final PsiExpression leftExpression = expression.getLExpression();
      final PsiExpression rightExpression = expression.getRExpression();
      assert rightExpression != null;
      final String newExpression =
        leftExpression.getText() + "==" + rightExpression.getText();
      replaceExpression(expression, newExpression);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentUsedAsConditionVisitor();
  }

  private static class AssignmentUsedAsConditionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(
      @Nonnull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (!WellFormednessUtils.isWellFormed(expression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (parent == null) {
        return;
      }
      if (parent instanceof PsiIfStatement) {
        checkIfStatementCondition((PsiIfStatement)parent, expression);
      }
      if (parent instanceof PsiWhileStatement) {
        checkWhileStatementCondition((PsiWhileStatement)parent,
                                     expression);
      }
      if (parent instanceof PsiForStatement) {
        checkForStatementCondition((PsiForStatement)parent, expression);
      }
      if (parent instanceof PsiDoWhileStatement) {
        checkDoWhileStatementCondition((PsiDoWhileStatement)parent,
                                       expression);
      }
    }

    private void checkIfStatementCondition(
      PsiIfStatement ifStatement, PsiAssignmentExpression expression) {
      final PsiExpression condition = ifStatement.getCondition();
      if (expression.equals(condition)) {
        registerError(expression);
      }
    }

    private void checkDoWhileStatementCondition(
      PsiDoWhileStatement doWhileStatement, PsiAssignmentExpression expression) {
      final PsiExpression condition = doWhileStatement.getCondition();
      if (expression.equals(condition)) {
        registerError(expression);
      }
    }

    private void checkForStatementCondition(
      PsiForStatement forStatement, PsiAssignmentExpression expression) {
      final PsiExpression condition = forStatement.getCondition();
      if (expression.equals(condition)) {
        registerError(expression);
      }
    }

    private void checkWhileStatementCondition(
      PsiWhileStatement whileStatement, PsiAssignmentExpression expression) {
      final PsiExpression condition = whileStatement.getCondition();
      if (expression.equals(condition)) {
        registerError(expression);
      }
    }
  }
}