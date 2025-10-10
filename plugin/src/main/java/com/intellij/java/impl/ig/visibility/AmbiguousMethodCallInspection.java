/*
 * Copyright 2008-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class AmbiguousMethodCallInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.ambiguousMethodCallDisplayName();
  }

  @Nonnull
  @RequiredReadAction
  protected String buildErrorString(Object... infos) {
    final PsiClass superClass = (PsiClass)infos[0];
    final PsiClass outerClass = (PsiClass)infos[1];
    return InspectionGadgetsLocalize.ambiguousMethodCallProblemDescriptor(superClass.getName(), outerClass.getName()).get();
  }

  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AmbiguousMethodCallFix();
  }

  private static class AmbiguousMethodCallFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.ambiguousMethodCallQuickfix().get();
    }

    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent.getParent();
      final String newExpressionText = "super." + methodCallExpression.getText();
      replaceExpression(methodCallExpression, newExpressionText);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AmbiguousMethodCallVisitor();
  }

  private static class AmbiguousMethodCallVisitor extends BaseInspectionVisitor {

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null) {
        return;
      }
      PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null || !containingClass.isInheritor(methodClass, true)) {
        return;
      }
      containingClass = ClassUtils.getContainingClass(containingClass);
      final String methodName = methodExpression.getReferenceName();
      while (containingClass != null) {
        final PsiMethod[] methods = containingClass.findMethodsByName(methodName, false);
        if (methods.length > 0 && !methodClass.equals(containingClass)) {
          registerMethodCallError(expression, methodClass, containingClass);
          return;
        }
        containingClass = ClassUtils.getContainingClass(containingClass);
      }
    }
  }
}