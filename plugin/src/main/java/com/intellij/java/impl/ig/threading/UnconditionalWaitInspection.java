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
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class UnconditionalWaitInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.unconditionalWaitDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.unconditionalWaitProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnconditionalWaitVisitor();
  }

  private static class UnconditionalWaitVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return;
      }
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        checkBody(body);
      }
    }

    @Override
    public void visitSynchronizedStatement(
      @Nonnull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      PsiCodeBlock body = statement.getBody();
      if (body != null) {
        checkBody(body);
      }
    }

    private void checkBody(PsiCodeBlock body) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return;
      }
      for (PsiStatement statement : statements) {
        if (isConditional(statement)) {
          return;
        }
        if (!(statement instanceof PsiExpressionStatement)) {
          continue;
        }
        PsiExpression firstExpression =
          ((PsiExpressionStatement)statement).getExpression();
        if (!(firstExpression instanceof PsiMethodCallExpression)) {
          continue;
        }
        PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)firstExpression;
        PsiReferenceExpression methodExpression =
          methodCallExpression.getMethodExpression();
        @NonNls String methodName =
          methodExpression.getReferenceName();
        if (!HardcodedMethodConstants.WAIT.equals(methodName)) {
          continue;
        }
        PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
          continue;
        }
        PsiParameterList parameterList =
          method.getParameterList();
        int numParams = parameterList.getParametersCount();
        if (numParams > 2) {
          continue;
        }
        PsiParameter[] parameters = parameterList.getParameters();
        if (numParams > 0) {
          PsiType parameterType = parameters[0].getType();
          if (!parameterType.equals(PsiType.LONG)) {
            continue;
          }
        }
        if (numParams > 1) {
          PsiType parameterType = parameters[1].getType();
          if (!parameterType.equals(PsiType.INT)) {
            continue;
          }
        }
        registerMethodCallError(methodCallExpression);
      }
    }

    private static boolean isConditional(PsiStatement statement) {
      return statement instanceof PsiIfStatement;
    }
  }
}