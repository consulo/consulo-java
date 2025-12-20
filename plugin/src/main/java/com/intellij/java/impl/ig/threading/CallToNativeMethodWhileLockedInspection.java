/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.SynchronizationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CallToNativeMethodWhileLockedInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.callToNativeMethodWhileLockedDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.callToNativeMethodWhileLockedProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToNativeMethodWhileLockedVisitor();
  }

  private static class CallToNativeMethodWhileLockedVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.NATIVE)) {
        return;
      }
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      String className = containingClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(className)) {
        return;
      }
      if (!SynchronizationUtil.isInSynchronizedContext(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}