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

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class MethodOverridesPackageLocalMethodInspection
  extends BaseInspection {

  @Nonnull
  public String getID() {
    return "MethodOverridesPrivateMethodOfSuperclass";
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.methodOverridesPackageLocalMethodDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.methodOverridesPackageLocalMethodProblemDescriptor().get();
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
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      PsiClass ancestorClass = aClass.getSuperClass();
      Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
      while (ancestorClass != null) {
        if (!visitedClasses.add(ancestorClass)) {
          return;
        }
        PsiMethod overridingMethod =
          ancestorClass.findMethodBySignature(method, true);
        if (overridingMethod != null) {
          if (overridingMethod.hasModifierProperty(
            PsiModifier.PACKAGE_LOCAL)) {
            PsiJavaFile file =
              PsiTreeUtil.getParentOfType(aClass,
                                          PsiJavaFile.class);
            if (file == null) {
              return;
            }
            PsiJavaFile ancestorFile =
              PsiTreeUtil.getParentOfType(ancestorClass,
                                          PsiJavaFile.class);
            if (ancestorFile == null) {
              return;
            }
            String packageName = file.getPackageName();
            String ancestorPackageName =
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