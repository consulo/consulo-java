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

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiSwitchLabelStatement;
import com.intellij.java.language.psi.PsiSwitchStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class DefaultNotLastCaseInSwitchInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "default.not.last.case.in.switch.display.name");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "default.not.last.case.in.switch.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new DefaultNotLastCaseInSwitchVisitor();
  }

  private static class DefaultNotLastCaseInSwitchVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(
      @Nonnull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      boolean labelSeen = false;
      for (int i = statements.length - 1; i >= 0; i--) {
        final PsiStatement child = statements[i];
        if (child instanceof PsiSwitchLabelStatement) {
          final PsiSwitchLabelStatement label =
            (PsiSwitchLabelStatement)child;
          if (label.isDefaultCase()) {
            if (labelSeen) {
              registerStatementError(label);
            }
            return;
          }
          else {
            labelSeen = true;
          }
        }
      }
    }
  }
}