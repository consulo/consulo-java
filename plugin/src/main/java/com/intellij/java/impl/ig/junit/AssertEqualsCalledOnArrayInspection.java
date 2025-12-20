/*
 * Copyright 2010-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.impl.ig.psiutils.ImportUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class AssertEqualsCalledOnArrayInspection extends BaseInspection {
  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.assertequalsCalledOnArraysDisplayName();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.assertequalsCalledOnArraysProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AssertEqualsCalledOnArrayFix();
  }

  private static class AssertEqualsCalledOnArrayFix extends InspectionGadgetsFix {

    @Override
    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.assertequalsCalledOnArraysQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement methodNameIdentifier = descriptor.getPsiElement();
      PsiElement parent = methodNameIdentifier.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null && ImportUtils.addStaticImport("org.junit.Assert", "assertArrayEquals", methodExpression)) {
        replaceExpression(methodExpression, "assertArrayEquals");
      }
      else {
        replaceExpression(methodExpression, "org.junit.Assert.assertArrayEquals");
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsOnArrayVisitor();
  }

  private static class AssertEqualsOnArrayVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls String methodName = methodExpression.getReferenceName();
      if (!"assertEquals".equals(methodName)) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      PsiType type1;
      PsiType type2;
      if (arguments.length == 2) {
        PsiExpression argument0 = arguments[0];
        type1 = argument0.getType();
        PsiExpression argument1 = arguments[1];
        type2 = argument1.getType();
      }
      else if (arguments.length == 3) {
        PsiExpression argument0 = arguments[1];
        type1 = argument0.getType();
        PsiExpression argument1 = arguments[2];
        type2 = argument1.getType();
      }
      else {
        return;
      }
      if (!(type1 instanceof PsiArrayType) || !(type2 instanceof PsiArrayType)) {
        return;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
          !InheritanceUtil.isInheritor(containingClass, "org.junit.Assert") &&
          !InheritanceUtil.isInheritor(containingClass, "org.testng.AssertJUnit")) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
