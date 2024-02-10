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
package com.intellij.java.impl.ig.cloneable;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.MakeCloneableFix;
import com.siyeh.ig.psiutils.CloneUtils;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CloneInNonCloneableClassInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "clone.method.in.non.cloneable.class.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    if (aClass.isInterface()) {
      return InspectionGadgetsBundle.message(
        "clone.method.in.non.cloneable.interface.problem.descriptor",
        className);
    }
    else {
      return InspectionGadgetsBundle.message(
        "clone.method.in.non.cloneable.class.problem.descriptor",
        className);
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return new MakeCloneableFix(aClass.isInterface());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneInNonCloneableClassVisitor();
  }

  private static class CloneInNonCloneableClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (!CloneUtils.isClone(method)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null ||
          CloneUtils.isCloneable(containingClass)) {
        return;
      }
      registerMethodError(method, containingClass);
    }
  }
}