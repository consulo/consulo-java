/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.PsiBlockStatement;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiSwitchLabelStatement;
import consulo.project.Project;
import com.intellij.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.psiutils.VariableSearchUtils;
import javax.annotation.Nonnull;

import javax.swing.*;

public class UnnecessaryBlockStatementInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSwitchBranches = false;

  @Override
  @Nonnull
  public String getID() {
    return "UnnecessaryCodeBlock";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.code.block.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.block.statement.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("ignore.branches.of.switch.statements"),
      this, "ignoreSwitchBranches");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryBlockStatementVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryBlockFix();
  }

  private static class UnnecessaryBlockFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.code.block.unwrap.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement leftBrace = descriptor.getPsiElement();
      final PsiElement parent = leftBrace.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return;
      }
      final PsiCodeBlock block = (PsiCodeBlock)parent;
      final PsiBlockStatement blockStatement =
        (PsiBlockStatement)block.getParent();
      final PsiElement[] children = block.getChildren();
      if (children.length > 2) {
        final PsiElement element = blockStatement.getParent();
        element.addRangeBefore(children[1],
                               children[children.length - 2], blockStatement);
      }
      blockStatement.delete();
    }
  }

  private class UnnecessaryBlockStatementVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBlockStatement(
      PsiBlockStatement blockStatement) {
      super.visitBlockStatement(blockStatement);
      if (ignoreSwitchBranches) {
        final PsiElement prevStatement =
          PsiTreeUtil.skipSiblingsBackward(blockStatement,
                                           PsiWhiteSpace.class);
        if (prevStatement instanceof PsiSwitchLabelStatement) {
          return;
        }
      }
      final PsiElement parent = blockStatement.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return;
      }
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiJavaToken brace = codeBlock.getLBrace();
      if (brace == null) {
        return;
      }
      final PsiCodeBlock parentBlock = (PsiCodeBlock)parent;
      if (parentBlock.getStatements().length > 1 &&
          VariableSearchUtils.containsConflictingDeclarations(
            codeBlock, parentBlock)) {
        return;
      }
      registerError(brace);
      final PsiJavaToken rbrace = codeBlock.getRBrace();
      if (rbrace != null) {
        registerError(rbrace);
      }
    }
  }
}