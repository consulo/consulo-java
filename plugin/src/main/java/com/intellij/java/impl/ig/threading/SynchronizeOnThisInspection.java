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
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SynchronizeOnThisInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.synchronizeOnThisDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.synchronizeOnThisProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnThisVisitor();
  }

  private static class SynchronizeOnThisVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(
      @Nonnull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      PsiExpression lockExpression = statement.getLockExpression();
      if (!(lockExpression instanceof PsiThisExpression)) {
        return;
      }
      registerError(lockExpression);
    }

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier != null &&
          !(qualifier instanceof PsiThisExpression)) {
        return;
      }
      if (!isNotify(expression) && !isWait(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean isWait(PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();

      if (!HardcodedMethodConstants.WAIT.equals(methodName)) {
        return false;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      PsiParameterList parameterList = method.getParameterList();
      int numParams = parameterList.getParametersCount();
      if (numParams > 2) {
        return false;
      }
      PsiParameter[] parameters = parameterList.getParameters();
      if (numParams > 0) {
        PsiType parameterType = parameters[0].getType();
        if (!parameterType.equals(PsiType.LONG)) {
          return false;
        }
      }

      if (numParams > 1) {
        PsiType parameterType = parameters[1].getType();
        if (!parameterType.equals(PsiType.INT)) {
          return false;
        }
      }
      return true;
    }

    private static boolean isNotify(PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.NOTIFY.equals(methodName) &&
          !HardcodedMethodConstants.NOTIFY_ALL.equals(methodName)) {
        return false;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      PsiParameterList parameterList = method.getParameterList();
      return parameterList.getParametersCount() == 0;
    }
  }
}