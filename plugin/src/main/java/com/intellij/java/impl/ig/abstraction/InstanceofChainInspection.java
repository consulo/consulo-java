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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.impl.ig.psiutils.LibraryUtil;
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
public class InstanceofChainInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInstanceofOnLibraryClasses = false;

  @Override
  @Nonnull
  public String getID() {
    return "ChainOfInstanceofChecks";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.chainOfInstanceofChecksDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.chainOfInstanceofChecksProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.ignoreInstanceofOnLibraryClasses();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreInstanceofOnLibraryClasses");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofChainVisitor();
  }

  private class InstanceofChainVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(
      @Nonnull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        PsiIfStatement parentStatement = (PsiIfStatement)parent;
        PsiStatement elseBranch = parentStatement.getElseBranch();
        if (statement.equals(elseBranch)) {
          return;
        }
      }
      int numChecks = 0;
      PsiIfStatement branch = statement;
      while (branch != null) {
        PsiExpression condition = branch.getCondition();
        if (!isInstanceofCheck(condition)) {
          return;
        }
        numChecks++;
        PsiStatement elseBranch = branch.getElseBranch();
        if (elseBranch instanceof PsiIfStatement) {
          branch = (PsiIfStatement)elseBranch;
        }
        else {
          branch = null;
        }
      }
      if (numChecks < 2) {
        return;
      }
      registerStatementError(statement);
    }

    private boolean isInstanceofCheck(PsiExpression condition) {
      while (true) {
        if (condition == null) {
          return false;
        }
        else if (condition instanceof PsiInstanceOfExpression) {
          if (ignoreInstanceofOnLibraryClasses) {
            PsiInstanceOfExpression instanceOfExpression =
              (PsiInstanceOfExpression)condition;
            if (isInstanceofOnLibraryClass(instanceOfExpression)) {
              return false;
            }
          }
          return true;
        }
        else if (condition instanceof PsiPolyadicExpression) {
          PsiPolyadicExpression polyadicExpression =
            (PsiPolyadicExpression)condition;
          PsiExpression[] operands =
            polyadicExpression.getOperands();
          for (PsiExpression operand : operands) {
            if (!isInstanceofCheck(operand)) {
              return false;
            }
          }
          return true;
        }
        else if (condition instanceof PsiParenthesizedExpression) {
          PsiParenthesizedExpression parenthesizedExpression =
            (PsiParenthesizedExpression)condition;
          condition = parenthesizedExpression.getExpression();
          continue;
        }
        else if (condition instanceof PsiPrefixExpression) {
          PsiPrefixExpression prefixExpression =
            (PsiPrefixExpression)condition;
          condition = prefixExpression.getOperand();
          continue;
        }
        else if (condition instanceof PsiPostfixExpression) {
          PsiPostfixExpression postfixExpression =
            (PsiPostfixExpression)condition;
          condition = postfixExpression.getOperand();
          continue;
        }
        return false;
      }
    }

    private boolean isInstanceofOnLibraryClass(
      PsiInstanceOfExpression instanceOfExpression) {
      PsiTypeElement checkType =
        instanceOfExpression.getCheckType();
      if (checkType == null) {
        return false;
      }
      PsiType type = checkType.getType();
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      PsiClassType classType = (PsiClassType)type;
      PsiClass aClass = classType.resolve();
      return LibraryUtil.classIsInLibrary(aClass);
    }
  }
}