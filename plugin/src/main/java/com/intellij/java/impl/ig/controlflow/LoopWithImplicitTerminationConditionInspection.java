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
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

@ExtensionImpl
public class LoopWithImplicitTerminationConditionInspection
  extends BaseInspection {

  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.loopWithImplicitTerminationConditionDisplayName().get();
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

  private static class LoopWithImplicitTerminationConditionFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.loopWithImplicitTerminationConditionQuickfix().get();
    }

    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiExpression loopCondition;
      final PsiStatement body;
      final boolean firstStatement;
      if (parent instanceof PsiWhileStatement) {
        final PsiWhileStatement whileStatement =
          (PsiWhileStatement)parent;
        loopCondition = whileStatement.getCondition();
        body = whileStatement.getBody();
        firstStatement = true;
      }
      else if (parent instanceof PsiDoWhileStatement) {
        final PsiDoWhileStatement doWhileStatement =
          (PsiDoWhileStatement)parent;
        loopCondition = doWhileStatement.getCondition();
        body = doWhileStatement.getBody();
        firstStatement = false;
      }
      else if (parent instanceof PsiForStatement) {
        final PsiForStatement forStatement = (PsiForStatement)parent;
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
      final PsiStatement statement;
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
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
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      final PsiExpression ifCondition = ifStatement.getCondition();
      if (ifCondition == null) {
        return;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (containsUnlabeledBreakStatement(thenBranch)) {
        final String negatedExpressionText =
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
      @Nullable PsiStatement replacingStatement)
      throws IncorrectOperationException {
      if (replacingStatement == null) {
        replacedStatement.delete();
        return;
      }
      if (!(replacingStatement instanceof PsiBlockStatement)) {
        replacedStatement.replace(replacingStatement);
        return;
      }
      final PsiBlockStatement blockStatement =
        (PsiBlockStatement)replacingStatement;
      final PsiCodeBlock codeBlock =
        blockStatement.getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        final PsiElement receiver = replacedStatement.getParent();
        for (int i = children.length - 2; i > 0; i--) {
          final PsiElement child = children[i];
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
      final PsiExpression condition = statement.getCondition();
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
      final PsiExpression condition = statement.getCondition();
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
      final PsiExpression condition = statement.getCondition();
      if (!BoolUtils.isTrue(condition)) {
        return;
      }
      if (isLoopWithImplicitTerminationCondition(statement, true)) {
        return;
      }
      registerStatementError(statement, Boolean.FALSE);
    }

    private static boolean isLoopWithImplicitTerminationCondition(
      PsiLoopStatement statement, boolean firstStatement) {
      final PsiStatement body = statement.getBody();
      final PsiStatement bodyStatement;
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
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
      @Nullable PsiStatement statement) {
      if (!(statement instanceof PsiIfStatement)) {
        return false;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (containsUnlabeledBreakStatement(thenBranch)) {
        return true;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      return containsUnlabeledBreakStatement(elseBranch);
    }
  }

  static boolean containsUnlabeledBreakStatement(
    @Nullable PsiStatement statement) {
    if (!(statement instanceof PsiBlockStatement)) {
      return isUnlabeledBreakStatement(statement);
    }
    final PsiBlockStatement blockStatement =
      (PsiBlockStatement)statement;
    final PsiCodeBlock codeBlock =
      blockStatement.getCodeBlock();
    final PsiStatement[] statements = codeBlock.getStatements();
    if (statements.length != 1) {
      return false;
    }
    final PsiStatement firstStatement = statements[0];
    return isUnlabeledBreakStatement(firstStatement);
  }

  private static boolean isUnlabeledBreakStatement(
    @Nullable PsiStatement statement) {
    if (!(statement instanceof PsiBreakStatement)) {
      return false;
    }
    final PsiBreakStatement breakStatement =
      (PsiBreakStatement)statement;
    final PsiIdentifier identifier =
      breakStatement.getLabelIdentifier();
    return identifier == null;
  }
}