/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NullThrownInspection extends BaseInspection {

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("null.thrown.display.name");
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "null.thrown.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ThrowNullFix();
  }

  private static class ThrowNullFix extends InspectionGadgetsFix {

    @Nonnull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("null.thrown.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiExpression newExpression =
        factory.createExpressionFromText(
          "new java.lang.NullPointerException()", element);
      element.replace(newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowNullVisitor();
  }

  private static class ThrowNullVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception =
        ParenthesesUtils.stripParentheses(statement.getException());
      if (!(exception instanceof PsiLiteralExpression)) {
        return;
      }
      final PsiType type = exception.getType();
      if (!PsiType.NULL.equals(type)) {
        return;
      }
      registerError(exception);
    }
  }
}
