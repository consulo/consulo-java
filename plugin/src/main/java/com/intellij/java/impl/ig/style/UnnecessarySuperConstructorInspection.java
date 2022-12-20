/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import javax.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;

@ExtensionImpl
public class UnnecessarySuperConstructorInspection
  extends BaseInspection {

  @Nonnull
  public String getID() {
    return "UnnecessaryCallToSuper";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.super.constructor.display.name");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.super.constructor.problem.descriptor");
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessarySuperConstructorFix();
  }

  private static class UnnecessarySuperConstructorFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.super.constructor.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement superCall = descriptor.getPsiElement();
      final PsiElement callStatement = superCall.getParent();
      assert callStatement != null;
      deleteElement(callStatement);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarySuperConstructorVisitor();
  }

  private static class UnnecessarySuperConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      final String methodText = methodExpression.getText();
      if (!PsiKeyword.SUPER.equals(methodText)) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 0) {
        return;
      }
      registerError(call, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }
  }
}