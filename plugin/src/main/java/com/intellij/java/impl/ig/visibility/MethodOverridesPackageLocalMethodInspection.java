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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import javax.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

public class MethodOverridesPackageLocalMethodInspection
  extends BaseInspection {

  @Nonnull
  public String getID() {
    return "MethodOverridesPrivateMethodOfSuperclass";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "method.overrides.package.local.method.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "method.overrides.package.local.method.problem.descriptor");
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MethodOverridesPrivateMethodVisitor();
  }

  private static class MethodOverridesPrivateMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      PsiClass ancestorClass = aClass.getSuperClass();
      final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
      while (ancestorClass != null) {
        if (!visitedClasses.add(ancestorClass)) {
          return;
        }
        final PsiMethod overridingMethod =
          ancestorClass.findMethodBySignature(method, true);
        if (overridingMethod != null) {
          if (overridingMethod.hasModifierProperty(
            PsiModifier.PACKAGE_LOCAL)) {
            final PsiJavaFile file =
              PsiTreeUtil.getParentOfType(aClass,
                                          PsiJavaFile.class);
            if (file == null) {
              return;
            }
            final PsiJavaFile ancestorFile =
              PsiTreeUtil.getParentOfType(ancestorClass,
                                          PsiJavaFile.class);
            if (ancestorFile == null) {
              return;
            }
            final String packageName = file.getPackageName();
            final String ancestorPackageName =
              ancestorFile.getPackageName();
            if (!packageName.equals(ancestorPackageName)) {
              registerMethodError(method);
              return;
            }
          }
        }
        ancestorClass = ancestorClass.getSuperClass();
      }
    }
  }
}