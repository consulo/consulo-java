/*
 * Copyright 2008-2009 Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class UnnecessaryConstantArrayCreationExpressionInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryConstantArrayCreationExpressionDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.unnecessaryConstantArrayCreationExpressionProblemDescriptor().get();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryConstantArrayCreationExpressionFix();
  }

  private static class UnnecessaryConstantArrayCreationExpressionFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.constant.array.creation.expression.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)element;
      final PsiArrayInitializerExpression arrayInitializer =
        newExpression.getArrayInitializer();
      if (arrayInitializer == null) {
        return;
      }
      newExpression.replace(arrayInitializer);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryConstantArrayCreationExpressionVisitor();
  }

  private static class UnnecessaryConstantArrayCreationExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitArrayInitializerExpression(
      PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiVariable)) {
        return;
      }
      final PsiVariable variable = (PsiVariable)grandParent;
      if (hasGenericTypeParameters(variable)) {
        return;
      }
      registerError(parent);
    }

    private static boolean hasGenericTypeParameters(PsiVariable variable) {
      final PsiType type = variable.getType();
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)componentType;
      final PsiType[] parameterTypes = classType.getParameters();
      for (PsiType parameterType : parameterTypes) {
        if (parameterType != null) {
          return true;
        }
      }
      return false;
    }
  }
}