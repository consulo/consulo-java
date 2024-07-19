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
package com.intellij.java.impl.ig.classmetrics;

import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class ClassInheritanceDepthInspection
  extends ClassMetricInspection {

  @Nonnull
  public String getID() {
    return "ClassTooDeepInInheritanceTree";
  }

  private static final int CLASS_INHERITANCE_LIMIT = 2;

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.classTooDeepDisplayName().get();
  }

  protected int getDefaultLimit() {
    return CLASS_INHERITANCE_LIMIT;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsLocalize.classTooDeepInheritanceDepthLimitOption().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer count = (Integer)infos[0];
    return InspectionGadgetsLocalize.classTooDeepProblemDescriptor(count).get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ClassNestingLevel();
  }

  private class ClassNestingLevel extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      // note: no call to super
      if (aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final int inheritanceDepth =
        getInheritanceDepth(aClass, new HashSet<PsiClass>());
      if (inheritanceDepth <= getLimit()) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(inheritanceDepth));
    }

    private int getInheritanceDepth(PsiClass aClass, Set<PsiClass> visited) {
      if (visited.contains(aClass)) {
        return 0;
      }
      visited.add(aClass);
      final PsiClass superClass = aClass.getSuperClass();
      if (superClass == null) {
        return 0;
      }
      if (LibraryUtil.classIsInLibrary(aClass) &&
          LibraryUtil.classIsInLibrary(superClass)) {
        return 0;
      }
      return getInheritanceDepth(superClass, visited) + 1;
    }
  }
}