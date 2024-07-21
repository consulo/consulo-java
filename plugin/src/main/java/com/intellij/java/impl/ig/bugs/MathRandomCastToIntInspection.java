/*
 * Copyright 2011-2012 Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class MathRandomCastToIntInspection extends BaseInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.mathRandomCastToIntDisplayName().get();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("math.random.cast.to.int.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiTypeCastExpression expression = (PsiTypeCastExpression)infos[0];
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiPolyadicExpression)) {
      return null;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (JavaTokenType.ASTERISK != tokenType) {
      return null;
    }
    return new MathRandomCastToIntegerFix();
  }

  private static class MathRandomCastToIntegerFix extends InspectionGadgetsFix {
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.mathRandomCastToIntQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
      final PsiElement grandParent = typeCastExpression.getParent();
      if (!(grandParent instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)grandParent;
      final PsiExpression operand = typeCastExpression.getOperand();
      if (operand == null) {
        return;
      }
      @NonNls final StringBuilder newExpression = new StringBuilder();
      newExpression.append("(int)(");
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (final PsiExpression expression : operands) {
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(expression);
        if (token != null) {
          newExpression.append(token.getText());
        }
        if (typeCastExpression.equals(expression)) {
          newExpression.append(operand.getText());
        }
        else {
          newExpression.append(expression.getText());
        }
      }
      newExpression.append(')');
      replaceExpression(polyadicExpression, newExpression.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MathRandomCastToIntegerVisitor();
  }

  private static class MathRandomCastToIntegerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiExpression operand = expression.getOperand();
      if (!(operand instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiTypeElement castType = expression.getCastType();
      if (castType == null) {
        return;
      }
      final PsiType type = castType.getType();
      if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)operand;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"random".equals(referenceName)) {
        return;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (!"java.lang.Math".equals(qualifiedName) && !"java.lang.StrictMath".equals(qualifiedName)) {
        return;
      }
      registerError(methodCallExpression, expression);
    }
  }
}
