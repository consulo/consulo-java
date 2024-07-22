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
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ThreadStartInConstructionInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "CallToThreadStartDuringObjectConstruction";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.threadStartInConstructionDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.threadStartInConstructionProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadStartInConstructionVisitor();
  }

  private static class ThreadStartInConstructionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (method.isConstructor()) {
        method.accept(new ThreadStartVisitor());
      }
    }

    @Override
    public void visitClassInitializer(
      @Nonnull PsiClassInitializer initializer) {
      if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
        initializer.accept(new ThreadStartVisitor());
      }
    }

    private class ThreadStartVisitor extends JavaRecursiveElementVisitor {

      @Override
      public void visitClass(PsiClass aClass) {
        // Do not recurse into.
      }

      @Override
      public void visitMethodCallExpression(
        @Nonnull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        final PsiReferenceExpression methodExpression =
          expression.getMethodExpression();
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!"start".equals(methodName)) {
          return;
        }
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiParameterList parameterList =
          method.getParameterList();
        if (parameterList.getParametersCount() != 0) {
          return;
        }
        final PsiClass methodClass = method.getContainingClass();
        if (methodClass == null ||
            !InheritanceUtil.isInheritor(methodClass,
                                         "java.lang.Thread")) {
          return;
        }
        final PsiClass containingClass =
          ClassUtils.getContainingClass(expression);
        if (containingClass == null ||
            containingClass.hasModifierProperty(
              PsiModifier.FINAL)) {
          return;
        }
        registerMethodCallError(expression);
      }
    }
  }
}