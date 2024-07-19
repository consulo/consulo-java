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

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ArrayEqualsInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.equalsCalledOnArrayDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.equalsCalledOnArrayProblemDescriptor().get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiArrayType type = (PsiArrayType)infos[0];
    if (type != null) {
      final PsiType componentType = type.getComponentType();
      if (componentType instanceof PsiArrayType) {
        return new ArrayEqualsFix(true);
      }
    }
    return new ArrayEqualsFix(false);
  }

  private static class ArrayEqualsFix extends InspectionGadgetsFix {

    private final boolean deepEquals;

    public ArrayEqualsFix(boolean deepEquals) {
      this.deepEquals = deepEquals;
    }

    @Nonnull
    public String getName() {
      return deepEquals
        ? InspectionGadgetsLocalize.replaceWithArraysDeepEquals().get()
        : InspectionGadgetsLocalize.replaceWithArraysEquals().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiIdentifier name = (PsiIdentifier)descriptor.getPsiElement();
      final PsiReferenceExpression expression = (PsiReferenceExpression)name.getParent();
      assert expression != null;
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression.getParent();
      final PsiExpression qualifier = expression.getQualifierExpression();
      assert qualifier != null;
      final String qualifierText = qualifier.getText();
      assert call != null;
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final String argumentText = arguments[0].getText();
      @NonNls final StringBuilder newExpressionText = new StringBuilder();
      if (deepEquals) {
        newExpressionText.append("java.util.Arrays.deepEquals(");
      }
      else {
        newExpressionText.append("java.util.Arrays.equals(");
      }
      newExpressionText.append(qualifierText);
      newExpressionText.append(", ");
      newExpressionText.append(argumentText);
      newExpressionText.append(')');
      replaceExpressionAndShorten(call, newExpressionText.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayEqualsVisitor();
  }

  private static class ArrayEqualsVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (argument == null) {
        return;
      }
      final PsiType argumentType = argument.getType();
      if (!(argumentType instanceof PsiArrayType)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType qualifierType = qualifier.getType();
      if (!(qualifierType instanceof PsiArrayType)) {
        return;
      }
      registerMethodCallError(expression, qualifierType);
    }
  }
}
