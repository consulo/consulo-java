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
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class WaitOrAwaitWithoutTimeoutInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.waitOrAwaitWithoutTimeoutDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.waitOrAwaitWithoutTimeoutProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new WaitWithoutTimeoutVisitor();
  }

  private static class WaitWithoutTimeoutVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!"wait".equals(methodName) && !"await".equals(methodName)) {
        return;
      }
      PsiExpressionList argList = expression.getArgumentList();
      PsiExpression[] args = argList.getExpressions();
      int numParams = args.length;
      if (numParams != 0) {
        return;
      }
      if ("await".equals(methodName)) {
        PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
          return;
        }
        String className = containingClass.getName();
        if (!"java.util.concurrent.locks.Condition".equals(className)) {
          return;
        }
      }
      registerMethodCallError(expression);
    }
  }
}