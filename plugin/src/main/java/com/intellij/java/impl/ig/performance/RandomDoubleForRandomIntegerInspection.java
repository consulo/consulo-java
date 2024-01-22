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
package com.intellij.java.impl.ig.performance;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class RandomDoubleForRandomIntegerInspection
  extends BaseInspection {

  @Override
  @jakarta.annotation.Nonnull
  public String getID() {
    return "UsingRandomNextDoubleForRandomInteger";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "random.double.for.random.integer.display.name");
  }

  @Override
  @jakarta.annotation.Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "random.double.for.random.integer.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RandomDoubleForRandomIntegerFix();
  }

  private static class RandomDoubleForRandomIntegerFix
    extends InspectionGadgetsFix {

    @jakarta.annotation.Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "random.double.for.random.integer.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiIdentifier name =
        (PsiIdentifier)descriptor.getPsiElement();
      final PsiReferenceExpression expression =
        (PsiReferenceExpression)name.getParent();
      if (expression == null) {
        return;
      }
      final PsiExpression call = (PsiExpression)expression.getParent();
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final String qualifierText = qualifier.getText();
      final PsiBinaryExpression multiplication =
        (PsiBinaryExpression)getContainingExpression(call);
      if (multiplication == null) {
        return;
      }
      final PsiExpression cast = getContainingExpression(multiplication);
      if (cast == null) {
        return;
      }
      final PsiExpression multiplierExpression;
      final PsiExpression lhs = multiplication.getLOperand();
      final PsiExpression strippedLhs =
        ParenthesesUtils.stripParentheses(lhs);
      if (call.equals(strippedLhs)) {
        multiplierExpression = multiplication.getROperand();
      }
      else {
        multiplierExpression = lhs;
      }
      assert multiplierExpression != null;
      final String multiplierText = multiplierExpression.getText();
      @NonNls final String nextInt = ".nextInt((int) ";
      replaceExpression(cast, qualifierText + nextInt + multiplierText +
                              ')');
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RandomDoubleForRandomIntegerVisitor();
  }

  private static class RandomDoubleForRandomIntegerVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @jakarta.annotation.Nonnull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String nextDouble = "nextDouble";
      if (!nextDouble.equals(methodName)) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!"java.util.Random".equals(className)) {
        return;
      }
      final PsiExpression possibleMultiplierExpression =
        getContainingExpression(call);
      if (!isMultiplier(possibleMultiplierExpression)) {
        return;
      }
      final PsiExpression possibleIntCastExpression =
        getContainingExpression(possibleMultiplierExpression);
      if (!isIntCast(possibleIntCastExpression)) {
        return;
      }
      registerMethodCallError(call);
    }

    private static boolean isMultiplier(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)expression;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      return JavaTokenType.ASTERISK.equals(tokenType);
    }

    private static boolean isIntCast(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (!(expression instanceof PsiTypeCastExpression)) {
        return false;
      }
      final PsiTypeCastExpression castExpression =
        (PsiTypeCastExpression)expression;
      final PsiType type = castExpression.getType();
      return PsiType.INT.equals(type);
    }
  }

  @Nullable
  static PsiExpression getContainingExpression(PsiExpression expression) {
    PsiElement ancestor = expression.getParent();
    while (true) {
      if (ancestor == null) {
        return null;
      }
      if (!(ancestor instanceof PsiExpression)) {
        return null;
      }
      if (!(ancestor instanceof PsiParenthesizedExpression)) {
        return (PsiExpression)ancestor;
      }
      ancestor = ancestor.getParent();
    }
  }
}