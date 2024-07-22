/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.initialization;

import com.intellij.java.impl.ig.fixes.MakeClassFinalFix;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class OverridableMethodCallDuringObjectConstructionInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.overridableMethodCallInConstructorDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.overridableMethodCallInConstructorProblemDescriptor().get();
  }

  @Override
  @Nonnull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[0];
    final PsiClass callClass = ClassUtils.getContainingClass(methodCallExpression);
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return InspectionGadgetsFix.EMPTY_ARRAY;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !containingClass.equals(callClass) || MethodUtils.isOverridden(method)) {
      return InspectionGadgetsFix.EMPTY_ARRAY;
    }
    final String methodName = method.getName();
    return new InspectionGadgetsFix[]{
      new MakeClassFinalFix(containingClass),
      new MakeMethodFinalFix(methodName)
    };
  }

  private static class MakeMethodFinalFix extends InspectionGadgetsFix {

    private final String methodName;

    MakeMethodFinalFix(String methodName) {
      this.methodName = methodName;
    }

    @Override
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.makeMethodFinalFixName(methodName).get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiElement methodExpression = methodName.getParent();
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)methodExpression.getParent();
      final PsiMethod method = methodCall.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiModifierList modifierList = method.getModifierList();
      modifierList.setModifierProperty(PsiModifier.FINAL, true);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverridableMethodCallInConstructorVisitor();
  }

  private static class OverridableMethodCallInConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallDuringObjectConstruction(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null) {
        if (!(qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression)) {
          return;
        }
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiMethod calledMethod = expression.resolveMethod();
      if (calledMethod == null || !PsiUtil.canBeOverriden(calledMethod)) {
        return;
      }
      final PsiClass calledMethodClass = calledMethod.getContainingClass();
      if (calledMethodClass == null || !calledMethodClass.equals(containingClass)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}