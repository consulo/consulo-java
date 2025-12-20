/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class WhileLoopSpinsOnFieldInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreNonEmtpyLoops = false;

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.whileLoopSpinsOnFieldDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.whileLoopSpinsOnFieldProblemDescriptor().get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.whileLoopSpinsOnFieldIgnoreNonEmptyLoopsOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreNonEmtpyLoops");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WhileLoopSpinsOnFieldVisitor();
  }

  private class WhileLoopSpinsOnFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@Nonnull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      PsiStatement body = statement.getBody();
      if (ignoreNonEmtpyLoops && !statementIsEmpty(body)) {
        return;
      }
      PsiExpression condition = statement.getCondition();
      PsiField field = getFieldIfSimpleFieldComparison(condition);
      if (field == null) {
        return;
      }
      if (body != null && VariableAccessUtils.variableIsAssigned(field, body)) {
        return;
      }
      registerStatementError(statement);
    }

    @Nullable
    private PsiField getFieldIfSimpleFieldComparison(PsiExpression condition) {
      condition = PsiUtil.deparenthesizeExpression(condition);
      if (condition == null) {
        return null;
      }
      PsiField field = getFieldIfSimpleFieldAccess(condition);
      if (field != null) {
        return field;
      }
      if (condition instanceof PsiPrefixExpression) {
        PsiPrefixExpression prefixExpression = (PsiPrefixExpression)condition;
        PsiExpression operand = prefixExpression.getOperand();
        return getFieldIfSimpleFieldComparison(operand);
      }
      if (condition instanceof PsiPostfixExpression) {
        PsiPostfixExpression postfixExpression = (PsiPostfixExpression)condition;
        PsiExpression operand = postfixExpression.getOperand();
        return getFieldIfSimpleFieldComparison(operand);
      }
      if (condition instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
        PsiExpression lOperand = binaryExpression.getLOperand();
        PsiExpression rOperand = binaryExpression.getROperand();
        if (ExpressionUtils.isLiteral(rOperand)) {
          return getFieldIfSimpleFieldComparison(lOperand);
        }
        else if (ExpressionUtils.isLiteral(lOperand)) {
          return getFieldIfSimpleFieldComparison(rOperand);
        }
        else {
          return null;
        }
      }
      return null;
    }

    @Nullable
    private PsiField getFieldIfSimpleFieldAccess(PsiExpression expression) {
      expression = PsiUtil.deparenthesizeExpression(expression);
      if (expression == null) {
        return null;
      }
      if (!(expression instanceof PsiReferenceExpression)) {
        return null;
      }
      PsiReferenceExpression reference = (PsiReferenceExpression)expression;
      PsiExpression qualifierExpression = reference.getQualifierExpression();
      if (qualifierExpression != null) {
        return null;
      }
      PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField)) {
        return null;
      }
      PsiField field = (PsiField)referent;
      if (field.hasModifierProperty(PsiModifier.VOLATILE)) {
        return null;
      }
      else {
        return field;
      }
    }

    private boolean statementIsEmpty(PsiStatement statement) {
      if (statement == null) {
        return false;
      }
      if (statement instanceof PsiEmptyStatement) {
        return true;
      }
      if (statement instanceof PsiBlockStatement) {
        PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
        PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        PsiStatement[] codeBlockStatements = codeBlock.getStatements();
        for (PsiStatement codeBlockStatement : codeBlockStatements) {
          if (!statementIsEmpty(codeBlockStatement)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }
}