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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.PsiBreakStatement;
import com.intellij.java.language.psi.PsiContinueStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ContinueOrBreakFromFinallyBlockInspection
  extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.continueOrBreakFromFinallyBlockDisplayName().get();
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.continueOrBreakFromFinallyBlockProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ContinueOrBreakFromFinallyBlockVisitor();
  }

  private static class ContinueOrBreakFromFinallyBlockVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitContinueStatement(
      @Nonnull PsiContinueStatement statement) {
      super.visitContinueStatement(statement);
      if (!ControlFlowUtils.isInFinallyBlock(statement)) {
        return;
      }
      final PsiStatement continuedStatement =
        statement.findContinuedStatement();
      if (continuedStatement == null) {
        return;
      }
      if (ControlFlowUtils.isInFinallyBlock(continuedStatement)) {
        return;
      }
      registerStatementError(statement);
    }

    @Override
    public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
      super.visitBreakStatement(statement);
      if (!ControlFlowUtils.isInFinallyBlock(statement)) {
        return;
      }
      final PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null) {
        return;
      }
      if (ControlFlowUtils.isInFinallyBlock(exitedStatement)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}