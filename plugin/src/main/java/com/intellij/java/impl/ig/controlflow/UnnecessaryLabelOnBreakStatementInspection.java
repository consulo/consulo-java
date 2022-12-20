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
package com.intellij.java.impl.ig.controlflow;

import javax.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;

@ExtensionImpl
public class UnnecessaryLabelOnBreakStatementInspection
  extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.label.on.break.statement.display.name");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.label.on.break.statement.problem.descriptor");
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryLabelOnBreakStatementFix();
  }

  private static class UnnecessaryLabelOnBreakStatementFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.label.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement breakKeywordElement = descriptor.getPsiElement();
      final PsiBreakStatement breakStatement =
        (PsiBreakStatement)breakKeywordElement.getParent();
      final PsiIdentifier identifier =
        breakStatement.getLabelIdentifier();
      if (identifier == null) {
        return;
      }
      identifier.delete();
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryLabelOnBreakStatementVisitor();
  }

  private static class UnnecessaryLabelOnBreakStatementVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
      final PsiIdentifier labelIdentifier =
        statement.getLabelIdentifier();
      if (labelIdentifier == null) {
        return;
      }
      final String labelText = labelIdentifier.getText();
      if (labelText == null || labelText.length() == 0) {
        return;
      }
      final PsiStatement exitedStatement =
        statement.findExitedStatement();
      if (exitedStatement == null) {
        return;
      }
      final PsiStatement labelEnabledParent =
        PsiTreeUtil.getParentOfType(statement,
                                    PsiForStatement.class, PsiDoWhileStatement.class,
                                    PsiForeachStatement.class, PsiWhileStatement.class,
                                    PsiSwitchStatement.class);
      if (labelEnabledParent == null) {
        return;
      }
      if (exitedStatement.equals(labelEnabledParent)) {
        registerStatementError(statement);
      }
    }
  }
}