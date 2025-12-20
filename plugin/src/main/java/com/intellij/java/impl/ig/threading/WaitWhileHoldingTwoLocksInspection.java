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
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class WaitWhileHoldingTwoLocksInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.waitWhileHoldingTwoLocksDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.waitWhileHoldingTwoLocksProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new WaitWhileHoldingTwoLocksVisitor();
  }

  private static class WaitWhileHoldingTwoLocksVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      checkErrorsIn(method);
    }

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      checkErrorsIn(initializer);
    }

    private void checkErrorsIn(PsiElement context) {
      context.accept(new JavaRecursiveElementVisitor() {
        private int m_numLocksHeld = 0;

        @Override
        public void visitClass(PsiClass aClass) {
          // Do not recurse into
        }

        @Override
        public void visitMethodCallExpression(
          @Nonnull PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (m_numLocksHeld < 2) {
            return;
          }
          PsiReferenceExpression methodExpression =
            expression.getMethodExpression();
          @NonNls String methodName =
            methodExpression.getReferenceName();
          if (!HardcodedMethodConstants.WAIT.equals(methodName)) {
            return;
          }
          PsiMethod method = expression.resolveMethod();
          if (method == null) {
            return;
          }
          PsiParameterList parameterList =
            method.getParameterList();
          int numParams = parameterList.getParametersCount();
          if (numParams > 2) {
            return;
          }
          PsiParameter[] parameters = parameterList.getParameters();
          if (numParams > 0) {
            PsiType parameterType = parameters[0].getType();
            if (!parameterType.equals(PsiType.LONG)) {
              return;
            }
          }
          if (numParams > 1) {
            PsiType parameterType = parameters[1].getType();
            if (!parameterType.equals(PsiType.INT)) {
              return;
            }
          }
          registerMethodCallError(expression);
        }

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
          if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            m_numLocksHeld++;
          }
          super.visitMethod(method);
          if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            m_numLocksHeld--;
          }
        }

        @Override
        public void visitSynchronizedStatement(
          @Nonnull PsiSynchronizedStatement synchronizedStatement) {
          m_numLocksHeld++;
          super.visitSynchronizedStatement(synchronizedStatement);
          m_numLocksHeld--;
        }
      });
    }
  }
}