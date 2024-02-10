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
package com.intellij.java.impl.ig.controlflow;

import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class UnnecessaryContinueInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInThenBranch = false;

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.continue.display.name");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.continue.problem.descriptor");
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.return.option"), this, "ignoreInThenBranch");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryContinueVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new DeleteUnnecessaryStatementFix("continue");
  }

  private class UnnecessaryContinueVisitor extends BaseInspectionVisitor {

    @Override
    public void visitContinueStatement(@Nonnull PsiContinueStatement statement) {
      /*if (JspPsiUtil.isInJspFile(statement.getContainingFile())) {
        return;
      }   */
      final PsiStatement continuedStatement = statement.findContinuedStatement();
      PsiStatement body = null;
      if (continuedStatement instanceof PsiForeachStatement) {
        final PsiForeachStatement foreachStatement = (PsiForeachStatement)continuedStatement;
        body = foreachStatement.getBody();
      }
      else if (continuedStatement instanceof PsiForStatement) {
        final PsiForStatement forStatement = (PsiForStatement)continuedStatement;
        body = forStatement.getBody();
      }
      else if (continuedStatement instanceof PsiDoWhileStatement) {
        final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)continuedStatement;
        body = doWhileStatement.getBody();
      }
      else if (continuedStatement instanceof PsiWhileStatement) {
        final PsiWhileStatement whileStatement = (PsiWhileStatement)continuedStatement;
        body = whileStatement.getBody();
      }
      if (body == null) {
        return;
      }
      if (ignoreInThenBranch && isInThenBranch(statement)) {
        return;
      }
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        if (ControlFlowUtils.blockCompletesWithStatement(block, statement)) {
          registerStatementError(statement);
        }
      }
      else if (ControlFlowUtils.statementCompletesWithStatement(body, statement)) {
        registerStatementError(statement);
      }
    }

    private boolean isInThenBranch(PsiStatement statement) {
      final PsiIfStatement ifStatement =
        PsiTreeUtil.getParentOfType(statement, PsiIfStatement.class, true, PsiMethod.class, PsiLambdaExpression.class);
      if (ifStatement == null) {
        return false;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      return elseBranch != null && !PsiTreeUtil.isAncestor(elseBranch, statement, true);
    }
  }
}