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
package com.intellij.java.impl.ig.errorhandling;

import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class UncheckedExceptionClassInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unchecked.exception.class.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unchecked.exception.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UncheckedExceptionClassVisitor();
  }

  private static class UncheckedExceptionClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@jakarta.annotation.Nonnull PsiClass aClass) {
      if (!InheritanceUtil.isInheritor(aClass,
                                       JavaClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      if (InheritanceUtil.isInheritor(aClass,
                                      JavaClassNames.JAVA_LANG_EXCEPTION) &&
          !InheritanceUtil.isInheritor(aClass,
                                       JavaClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
        return;
      }
      registerClassError(aClass);
    }
  }
}