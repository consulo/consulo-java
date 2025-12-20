/*
 * Copyright 2005-2011  Bas Leijdekkers
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

package com.intellij.java.impl.ig.migration;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class IndexOfReplaceableByContainsInspection
  extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.indexofReplaceableByContainsDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    PsiBinaryExpression expression = (PsiBinaryExpression)infos[0];
    PsiExpression lhs = expression.getLOperand();
    String text;
    if (lhs instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)lhs;
      text = createContainsExpressionText(callExpression, false,
                                          expression.getOperationTokenType());
    }
    else {
      PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)expression.getROperand();
      assert callExpression != null;
      text = createContainsExpressionText(callExpression, true,
                                          expression.getOperationTokenType());
    }
    return InspectionGadgetsLocalize.expressionCanBeReplacedProblemDescriptor(text).get();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IndexOfReplaceableByContainsFix();
  }

  private static class IndexOfReplaceableByContainsFix
    extends InspectionGadgetsFix {

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression)) {
        return;
      }
      PsiBinaryExpression expression =
        (PsiBinaryExpression)element;
      PsiExpression lhs = expression.getLOperand();
      PsiExpression rhs = expression.getROperand();
      String newExpressionText;
      if (lhs instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression callExpression =
          (PsiMethodCallExpression)lhs;
        newExpressionText =
          createContainsExpressionText(callExpression, false,
                                       expression.getOperationTokenType());
      }
      else if (rhs instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression callExpression =
          (PsiMethodCallExpression)rhs;
        newExpressionText =
          createContainsExpressionText(callExpression, true,
                                       expression.getOperationTokenType());
      }
      else {
        return;
      }
      replaceExpression(expression, newExpressionText);
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.replaceIndexofWithContainsQuickfix();
    }
  }

  static String createContainsExpressionText(
    @Nonnull PsiMethodCallExpression call,
    boolean flipped, IElementType tokenType) {
    PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    PsiExpression qualifierExpression =
      methodExpression.getQualifierExpression();
    String qualifierText;
    if (qualifierExpression == null) {
      qualifierText = "";
    }
    else {
      qualifierText = qualifierExpression.getText();
    }
    PsiExpressionList argumentList = call.getArgumentList();
    PsiExpression expression = argumentList.getExpressions()[0];
    @NonNls String newExpressionText =
      qualifierText + ".contains(" + expression.getText() + ')';
    if (tokenType.equals(JavaTokenType.EQEQ)) {
      return '!' + newExpressionText;
    }
    else if (!flipped && (tokenType.equals(JavaTokenType.LT) ||
                          tokenType.equals(JavaTokenType.LE))) {
      return '!' + newExpressionText;
    }
    else if (flipped && (tokenType.equals(JavaTokenType.GT) ||
                         tokenType.equals(JavaTokenType.GE))) {
      return '!' + newExpressionText;
    }
    return newExpressionText;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IndexOfReplaceableByContainsVisitor();
  }

  private static class IndexOfReplaceableByContainsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      PsiBinaryExpression expression) {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        return;
      }
      super.visitBinaryExpression(expression);
      PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      if (!ComparisonUtils.isComparison(expression)) {
        return;
      }
      PsiExpression lhs = expression.getLOperand();
      if (lhs instanceof PsiMethodCallExpression) {
        if (canBeReplacedByContains(lhs, rhs, false,
                                    expression.getOperationTokenType())) {
          registerError(expression, expression);
        }
      }
      else if (rhs instanceof PsiMethodCallExpression) {
        if (canBeReplacedByContains(rhs, lhs, true,
                                    expression.getOperationTokenType())) {
          registerError(expression, expression);
        }
      }
    }

    private static boolean canBeReplacedByContains(
      PsiExpression lhs,
      PsiExpression rhs, boolean flipped, IElementType tokenType) {
      PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)lhs;
      if (!isIndexOfCall(callExpression)) {
        return false;
      }
      Object object =
        ExpressionUtils.computeConstantExpression(rhs);
      if (!(object instanceof Integer)) {
        return false;
      }
      Integer integer = (Integer)object;
      int constant = integer.intValue();
      if (flipped) {
        if (constant == -1 && (JavaTokenType.NE.equals(tokenType) ||
                               JavaTokenType.LT.equals(tokenType) ||
                               JavaTokenType.EQEQ.equals(tokenType) ||
                               JavaTokenType.GE.equals(tokenType))) {
          return true;
        }
        else if (constant == 0 &&
                 (JavaTokenType.LE.equals(tokenType) ||
                  JavaTokenType.GT.equals(tokenType))) {
          return true;
        }
      }
      else {
        if (constant == -1 && (JavaTokenType.NE.equals(tokenType) ||
                               JavaTokenType.GT.equals(tokenType) ||
                               JavaTokenType.EQEQ.equals(tokenType) ||
                               JavaTokenType.LE.equals(tokenType))) {
          return true;
        }
        else if (constant == 0 &&
                 (JavaTokenType.GE.equals(tokenType) ||
                  JavaTokenType.LT.equals(tokenType))) {
          return true;
        }
      }
      return false;
    }

    private static boolean isIndexOfCall(
      @Nonnull PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.INDEX_OF.equals(methodName)) {
        return false;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return false;
      }
      PsiType argumentType = arguments[0].getType();
      if (argumentType == null || !argumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return false;
      }
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      PsiType qualifierType = qualifier.getType();
      return qualifierType != null && qualifierType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    }
  }
}
