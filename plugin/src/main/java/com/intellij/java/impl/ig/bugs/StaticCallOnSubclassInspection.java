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
package com.intellij.java.impl.ig.bugs;

import jakarta.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;

@ExtensionImpl
public class StaticCallOnSubclassInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "StaticMethodReferencedViaSubclass";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "static.method.via.subclass.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiClass declaringClass = (PsiClass)infos[0];
    final PsiClass referencedClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message(
      "static.method.via.subclass.problem.descriptor",
      declaringClass.getQualifiedName(), referencedClass.getQualifiedName());
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new StaticCallOnSubclassFix();
  }

  private static class StaticCallOnSubclassFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "static.method.via.subclass.rationalize.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiIdentifier name =
        (PsiIdentifier)descriptor.getPsiElement();
      final PsiReferenceExpression expression =
        (PsiReferenceExpression)name.getParent();
      if (expression == null) {
        return;
      }
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)expression.getParent();
      final String methodName = expression.getReferenceName();
      if (call == null) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      final PsiExpressionList argumentList = call.getArgumentList();
      if (containingClass == null) {
        return;
      }
      final String containingClassName =
        containingClass.getQualifiedName();
      final String argText = argumentList.getText();
      replaceExpressionAndShorten(call, containingClassName + '.' +
                                        methodName + argText);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new StaticCallOnSubclassVisitor();
  }

  private static class StaticCallOnSubclassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      final PsiElement qualifier = methodExpression.getQualifier();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiElement referent = ((PsiReference)qualifier).resolve();
      if (!(referent instanceof PsiClass)) {
        return;
      }
      final PsiClass referencedClass = (PsiClass)referent;
      final PsiClass declaringClass = method.getContainingClass();
      if (declaringClass == null) {
        return;
      }
      if (declaringClass.equals(referencedClass)) {
        return;
      }
      final PsiClass containingClass =
        ClassUtils.getContainingClass(call);
      if (!ClassUtils.isClassVisibleFromClass(containingClass,
                                              declaringClass)) {
        return;
      }
      registerMethodCallError(call, declaringClass, referencedClass);
    }
  }
}