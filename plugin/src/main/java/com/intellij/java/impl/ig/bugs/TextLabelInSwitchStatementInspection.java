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
package com.intellij.java.impl.ig.bugs;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class TextLabelInSwitchStatementInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "text.label.in.switch.statement.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "text.label.in.switch.statement.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new TextLabelInSwitchStatementVisitor();
  }

  private static class TextLabelInSwitchStatementVisitor
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
      for (PsiStatement statement1 : statements) {
        checkForLabel(statement1);
      }
    }

    private void checkForLabel(PsiStatement statement) {
      if (!(statement instanceof PsiLabeledStatement)) {
        return;
      }
      final PsiLabeledStatement labeledStatement =
        (PsiLabeledStatement)statement;
      final PsiIdentifier label = labeledStatement.getLabelIdentifier();
      registerError(label);
    }
  }
}