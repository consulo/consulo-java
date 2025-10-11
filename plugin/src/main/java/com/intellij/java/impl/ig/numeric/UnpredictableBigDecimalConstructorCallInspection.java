/*
 * Copyright 2007-2009 Bas Leijdekkers
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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

public abstract class UnpredictableBigDecimalConstructorCallInspection
  extends BaseInspection {

  public boolean ignoreReferences = true;
  public boolean ignoreComplexLiterals = false;

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.unpredictableBigDecimalConstructorCallDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.unpredictableBigDecimalConstructorCallProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.unpredictableBigDecimalConstructorCallIgnoreReferencesOption().get(),
      "ignoreReferences"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.unpredictableBigDecimalConstructorCallIgnoreComplexLiteralsOption().get(),
      "ignoreComplexLiterals"
    );
    return optionsPanel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiNewExpression newExpression = (PsiNewExpression)infos[0];
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return null;
    }
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 0) {
      return null;
    }
    final PsiExpression firstArgument = arguments[0];
    if (!(firstArgument instanceof PsiLiteralExpression)) {
      return null;
    }
    return new ReplaceDoubleArgumentWithStringFix(firstArgument.getText());
  }

  private class ReplaceDoubleArgumentWithStringFix
    extends InspectionGadgetsFix {

    private final String argumentText;

    public ReplaceDoubleArgumentWithStringFix(String argumentText) {
      this.argumentText = argumentText;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.unpredictableBigDecimalConstructorCallQuickfix(argumentText);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiNewExpression newExpression =
        (PsiNewExpression)element.getParent();
      final PsiExpressionList argumentList =
        newExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      replaceExpression(firstArgument,
                        '"' + firstArgument.getText() + '"');
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnpredictableBigDecimalConstructorCallVisitor();
  }

  private class UnpredictableBigDecimalConstructorCallVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiJavaCodeReferenceElement classReference =
        expression.getClassReference();
      if (classReference == null) {
        return;
      }
      final String name = classReference.getReferenceName();
      if (!"BigDecimal".equals(name)) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiParameterList parameterList =
        constructor.getParameterList();
      final int length = parameterList.getParametersCount();
      if (length != 1 && length != 2) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter firstParameter = parameters[0];
      final PsiType type = firstParameter.getType();
      if (!PsiType.DOUBLE.equals(type)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      if (!checkArguments(firstArgument)) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }

    private boolean checkArguments(@Nullable PsiExpression firstArgument) {
      if (firstArgument == null) {
        return false;
      }
      if (firstArgument instanceof PsiReferenceExpression) {
        if (ignoreReferences) {
          return false;
        }
      }
      else if (firstArgument instanceof PsiBinaryExpression) {
        if (ignoreComplexLiterals) {
          return false;
        }
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)firstArgument;
        final PsiExpression lhs = binaryExpression.getLOperand();
        if (!checkArguments(lhs)) {
          return false;
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        if (!checkArguments(rhs)) {
          return false;
        }
      }
      return true;
    }
  }
}