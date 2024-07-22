/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SleepWhileHoldingLockInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.sleepWhileHoldingLockDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.sleepWhileHoldingLockProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SleepWhileHoldingLockVisitor();
  }

  private static class SleepWhileHoldingLockVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"sleep".equals(methodName)) {
        return;
      }
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      boolean isSynced = false;
      if (containingMethod != null && containingMethod
        .hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        isSynced = true;
      }
      final PsiSynchronizedStatement containingSyncStatement =
        PsiTreeUtil.getParentOfType(expression,
                                    PsiSynchronizedStatement.class);
      if (containingSyncStatement != null) {
        isSynced = true;
      }
      if (!isSynced) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null ||
          !InheritanceUtil.isInheritor(methodClass,
                                       "java.lang.Thread")) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}