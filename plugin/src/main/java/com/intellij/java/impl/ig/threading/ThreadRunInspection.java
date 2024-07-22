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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ThreadRunInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.threadRunDisplayName().get();
  }

  @Override
  @Nonnull
  public String getID() {
    return "CallToThreadRun";
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.threadRunProblemDescriptor().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ThreadRunFix();
  }

  private static class ThreadRunFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.threadRunReplaceQuickfix().get();
    }

    @Override
    public void doFix(@Nonnull Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression =
        (PsiReferenceExpression)methodNameIdentifier.getParent();
      assert methodExpression != null;
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        replaceExpression(methodExpression, "start");
      }
      else {
        final String qualifierText = qualifier.getText();
        replaceExpression(methodExpression, qualifierText + ".start");
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThreadRunVisitor();
  }

  private static class ThreadRunVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.RUN.equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(methodClass, "java.lang.Thread")) {
        return;
      }
      if (isInsideThreadRun(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean isInsideThreadRun(
      PsiElement element) {
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method == null) {
        return false;
      }
      final String methodName = method.getName();
      if (!HardcodedMethodConstants.RUN.equals(methodName)) {
        return false;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null) {
        return false;
      }
      return InheritanceUtil.isInheritor(methodClass, "java.lang.Thread");
    }
  }
}