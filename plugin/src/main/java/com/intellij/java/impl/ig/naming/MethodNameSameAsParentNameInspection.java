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
package com.intellij.java.impl.ig.naming;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class MethodNameSameAsParentNameInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.methodNameSameAsParentNameDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.methodNameSameAsParentNameProblemDescriptor().get();
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MethodNameSameAsParentClassNameVisitor();
  }

  private static class MethodNameSameAsParentClassNameVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      // no call to super, so it doesn't drill down into inner classes
      if (method.isConstructor()) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiClass parent = containingClass.getSuperClass();
      if (parent == null) {
        return;
      }
      final String parentName = parent.getName();
      if (parentName == null) {
        return;
      }
      final String methodName = method.getName();
      if (!methodName.equals(parentName)) {
        return;
      }
      registerMethodError(method);
    }
  }
}