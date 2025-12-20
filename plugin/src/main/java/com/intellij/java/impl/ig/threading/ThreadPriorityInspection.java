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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ThreadPriorityInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "CallToThreadSetPriority";
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.threadPriorityDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.threadPriorityProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThreadSetPriorityVisitor();
  }

  private static class ThreadSetPriorityVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      if (!isThreadSetPriority(methodCallExpression)) {
        return;
      }
      if (hasNormalPriorityArgument(methodCallExpression)) {
        return;
      }
      registerMethodCallError(methodCallExpression);
    }

    private static boolean isThreadSetPriority(
      @Nonnull PsiMethodCallExpression methodCallExpression) {
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      @NonNls String setPriority = "setPriority";
      if (!setPriority.equals(methodName)) {
        return false;
      }
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      String className = aClass.getQualifiedName();
      return "java.lang.Thread".equals(className);
    }

    private static boolean hasNormalPriorityArgument(
      @Nonnull PsiMethodCallExpression methodCallExpression) {
      PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return false;
      }
      PsiExpression expression = expressions[0];
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      String referenceName = referenceExpression.getReferenceName();
      @NonNls String normPriority = "NORM_PRIORITY";
      if (!normPriority.equals(referenceName)) {
        return false;
      }
      PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiField)) {
        return false;
      }
      PsiField field = (PsiField)element;
      PsiClass aClass = field.getContainingClass();
      String className = aClass.getQualifiedName();
      return "java.lang.Thread".equals(className);
    }
  }
}