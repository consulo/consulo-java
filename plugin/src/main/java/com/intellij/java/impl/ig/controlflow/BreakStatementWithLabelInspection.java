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

import com.intellij.java.language.psi.PsiBreakStatement;
import com.intellij.java.language.psi.PsiIdentifier;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class BreakStatementWithLabelInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.breakStatementWithLabelDisplayName().get();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.breakStatementWithLabelProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new BreakStatementWithLabelVisitor();
  }

  private static class BreakStatementWithLabelVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
      super.visitBreakStatement(statement);
      final PsiIdentifier labelIdentifier =
        statement.getLabelIdentifier();
      if (labelIdentifier == null) {
        return;
      }

      final String labelText = labelIdentifier.getText();
      if (labelText == null) {
        return;
      }
      if (labelText.length() == 0) {
        return;
      }
      registerStatementError(statement);
    }
  }
}