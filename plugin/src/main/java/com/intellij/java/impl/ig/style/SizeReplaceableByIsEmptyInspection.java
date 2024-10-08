/*
 * Copyright 2006-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.OrderedSet;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

@ExtensionImpl
public class SizeReplaceableByIsEmptyInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreNegations = false;

  @SuppressWarnings("PublicField")
  public OrderedSet<String> ignoredTypes = new OrderedSet<>();

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.sizeReplaceableByIsemptyDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.expressionCanBeReplacedProblemDescriptor(infos[0]).get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(ignoredTypes, InspectionGadgetsLocalize.ignoredClassesTable().get()));
    JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsLocalize.chooseClassTypeToIgnore().get());
    final CheckBox checkBox =
      new CheckBox(InspectionGadgetsLocalize.sizeReplaceableByIsemptyNegationIgnoreOption().get(), this, "ignoreNegations");
    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SizeReplaceableByIsEmptyFix();
  }

  private static class SizeReplaceableByIsEmptyFix extends InspectionGadgetsFix {
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.sizeReplaceableByIsemptyQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) descriptor.getPsiElement();
      PsiExpression operand = binaryExpression.getLOperand();
      if (!(operand instanceof PsiMethodCallExpression)) {
        operand = binaryExpression.getROperand();
      }
      if (!(operand instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) operand;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      @NonNls String newExpression = qualifierExpression.getText();
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.EQEQ.equals(tokenType)) {
        newExpression = '!' + newExpression;
      }
      newExpression += ".isEmpty()";
      replaceExpression(binaryExpression, newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SizeReplaceableByIsEmptyVisitor();
  }

  private class SizeReplaceableByIsEmptyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      if (!ComparisonUtils.isComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (lhs instanceof PsiMethodCallExpression) {
        final String replacementIsEmptyCall = getReplacementIsEmptyCall(lhs, rhs, false, expression.getOperationTokenType());
        if (replacementIsEmptyCall != null) {
          registerError(expression, replacementIsEmptyCall);
        }
      } else if (rhs instanceof PsiMethodCallExpression) {
        final String replacementIsEmptyCall = getReplacementIsEmptyCall(rhs, lhs, true, expression.getOperationTokenType());
        if (replacementIsEmptyCall != null) {
          registerError(expression, replacementIsEmptyCall);
        }
      }
    }

    @Nullable
    private String getReplacementIsEmptyCall(PsiExpression lhs, PsiExpression rhs, boolean flipped, IElementType tokenType) {
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression) lhs;
      final String isEmptyCall = getIsEmptyCall(callExpression);
      if (isEmptyCall == null) {
        return null;
      }
      final Object object = ExpressionUtils.computeConstantExpression(rhs);
      if (!(object instanceof Integer)) {
        return null;
      }
      final Integer integer = (Integer) object;
      final int constant = integer.intValue();
      if (constant != 0) {
        return null;
      }
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        return isEmptyCall;
      }
      if (ignoreNegations) {
        return null;
      }
      if (JavaTokenType.NE.equals(tokenType)) {
        return '!' + isEmptyCall;
      } else if (flipped) {
        if (JavaTokenType.LT.equals(tokenType)) {
          return '!' + isEmptyCall;
        }
      } else if (JavaTokenType.GT.equals(tokenType)) {
        return '!' + isEmptyCall;
      }
      return null;
    }

    @Nullable
    private String getIsEmptyCall(PsiMethodCallExpression callExpression) {
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.SIZE.equals(referenceName) &&
          !HardcodedMethodConstants.LENGTH.equals(referenceName)) {
        return null;
      }
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 0) {
        return null;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return null;
      }
      final PsiType type = qualifierExpression.getType();
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      final PsiClassType classType = (PsiClassType) type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return null;
      }
      if (PsiTreeUtil.isAncestor(aClass, callExpression, true)) {
        return null;
      }
      for (String ignoredType : ignoredTypes) {
        if (InheritanceUtil.isInheritor(aClass, ignoredType)) {
          return null;
        }
      }
      final PsiMethod[] methods = aClass.findMethodsByName("isEmpty", true);
      for (PsiMethod method : methods) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() == 0) {
          return qualifierExpression.getText() + ".isEmpty()";
        }
      }
      return null;
    }
  }
}