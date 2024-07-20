/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class ConditionalExpressionInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSimpleAssignmentsAndReturns = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.conditionalExpressionDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.conditionalExpressionProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.conditionalExpressionOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreSimpleAssignmentsAndReturns");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConditionalExpressionVisitor();
  }

  private class ConditionalExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(
      PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      if (ignoreSimpleAssignmentsAndReturns) {
        PsiElement parent = expression.getParent();
        while (parent instanceof PsiParenthesizedExpression) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiAssignmentExpression ||
            parent instanceof PsiReturnStatement ||
            parent instanceof PsiLocalVariable) {
          return;
        }
      }
      registerError(expression);
    }
  }
}