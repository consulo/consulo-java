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

import com.intellij.java.impl.ig.psiutils.SwitchUtils;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiSwitchStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.deadCodeNotWorking.impl.SingleIntegerFieldOptionsPanel;

import javax.annotation.Nonnull;
import javax.swing.*;

public abstract class SwitchStatementWithTooManyBranchesInspection extends BaseInspection {

  private static final int DEFAULT_BRANCH_LIMIT = 10;
  /**
   * this is public for the DefaultJDOMExternalizer thingy
   *
   * @noinspection PublicField
   */
  public int m_limit = DEFAULT_BRANCH_LIMIT;

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "switch.statement.with.too.many.branches.display.name");
  }

  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(
      InspectionGadgetsBundle.message(
        "if.statement.with.too.many.branches.max.option"),
      this, "m_limit");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    final Integer branchCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "if.statement.with.too.many.branches.problem.descriptor",
      branchCount);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementWithTooManyBranchesVisitor();
  }

  private class SwitchStatementWithTooManyBranchesVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(
      @Nonnull PsiSwitchStatement statement) {
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return;
      }
      final int branchCount = SwitchUtils.calculateBranchCount(statement);
      if (branchCount <= m_limit) {
        return;
      }
      registerStatementError(statement, Integer.valueOf(branchCount));
    }
  }
}