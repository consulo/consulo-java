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

import javax.annotation.Nonnull;

import com.intellij.java.impl.ig.inheritance.ExtendsConcreteCollectionInspection;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.ReplaceInheritanceWithDelegationFix;

public class ExtendsThreadInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "ClassExplicitlyExtendsThread";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("extends.thread.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass instanceof PsiAnonymousClass) {
      return InspectionGadgetsBundle.message("anonymous.extends.thread.problem.descriptor");
    } else {
      return InspectionGadgetsBundle.message("extends.thread.problem.descriptor");
    }
  }

  /**
   * @see ExtendsConcreteCollectionInspection#buildFix(java.lang.Object...)
   */
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass instanceof PsiAnonymousClass) {
      return null;
    }
    return new ReplaceInheritanceWithDelegationFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsThreadVisitor();
  }

  private static class ExtendsThreadVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      final PsiClass superClass = aClass.getSuperClass();
      if (superClass == null) {
        return;
      }
      final String superclassName = superClass.getQualifiedName();
      if (!"java.lang.Thread".equals(superclassName)) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}