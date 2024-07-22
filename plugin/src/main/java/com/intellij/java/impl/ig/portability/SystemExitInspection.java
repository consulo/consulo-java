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
package com.intellij.java.impl.ig.portability;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SystemExitInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "CallToSystemExit";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.systemExitCallDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final String className = (String)infos[0];
    return InspectionGadgetsLocalize.systemExitCallProblemDescriptor(className).get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SystemExitVisitor();
  }

  private static class SystemExitVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String exit = "exit";
      @NonNls final String halt = "halt";
      if (!exit.equals(methodName) && !halt.equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }

      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType parameterType = parameters[0].getType();
      if (!parameterType.equals(PsiType.INT)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (className == null) {
        return;
      }
      if ("java.lang.System".equals(className)) {
        registerMethodCallError(expression, "System");
      }
      else if ("java.lang.Runtime".equals(className)) {
        registerMethodCallError(expression, "Runtime");
      }
    }
  }
}