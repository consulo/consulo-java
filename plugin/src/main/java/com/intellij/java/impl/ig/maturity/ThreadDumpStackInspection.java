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
package com.intellij.java.impl.ig.maturity;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ThreadDumpStackInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "CallToThreadDumpStack";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.dumpstackCallDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.dumpstackCallProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThreadDumpStackVisitor();
  }

  private static class ThreadDumpStackVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final String methodName = MethodCallUtils.getMethodName(expression);
      if (!HardcodedMethodConstants.DUMP_STACKTRACE.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList.getExpressions().length != 0) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiElement element = methodExpression.resolve();
      if (!(element instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)element;
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if (!"java.lang.Thread".equals(qualifiedName)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}