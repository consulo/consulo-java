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
package com.intellij.java.impl.ig.finalization;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NoExplicitFinalizeCallsInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "FinalizeCalledExplicitly";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "finalize.called.explicitly.display.name");
  }

  @jakarta.annotation.Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "finalize.called.explicitly.problem.descriptor");
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NoExplicitFinalizeCallsVisitor();
  }

  private static class NoExplicitFinalizeCallsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @jakarta.annotation.Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallToMethod(expression, null, PsiType.VOID,
                                          HardcodedMethodConstants.FINALIZE)) {
        return;
      }
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod == null) {
        return;
      }
      if (MethodUtils.methodMatches(containingMethod, null, PsiType.VOID,
                                    HardcodedMethodConstants.FINALIZE)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}