/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.project.Project;
import consulo.language.ast.IElementType;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NonShortCircuitBooleanInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "NonShortCircuitBooleanExpression";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.problem.descriptor");
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new NonShortCircuitBooleanFix();
  }

  private static class NonShortCircuitBooleanFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiPolyadicExpression expression = (PsiPolyadicExpression)descriptor.getPsiElement();
      final IElementType tokenType = expression.getOperationTokenType();
      final String operandText = getShortCircuitOperand(tokenType);
      final PsiExpression[] operands = expression.getOperands();
      final StringBuilder newExpression = new StringBuilder();
      for (PsiExpression operand : operands) {
        if (newExpression.length() != 0) {
          newExpression.append(operandText);
        }
        newExpression.append(operand.getText());
      }
      replaceExpression(expression, newExpression.toString());
    }

    private static String getShortCircuitOperand(IElementType tokenType) {
      if (tokenType.equals(JavaTokenType.AND)) {
        return "&&";
      }
      else {
        return "||";
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NonShortCircuitBooleanVisitor();
  }

  private static class NonShortCircuitBooleanVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.AND) && !tokenType.equals(JavaTokenType.OR)) {
        return;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!type.equals(PsiType.BOOLEAN)) {
        return;
      }
      registerError(expression);
    }
  }
}