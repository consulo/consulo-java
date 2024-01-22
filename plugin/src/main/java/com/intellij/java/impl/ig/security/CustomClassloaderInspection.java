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
package com.intellij.java.impl.ig.security;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class CustomClassloaderInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "custom.classloader.display.name");
  }

  @Override
  @jakarta.annotation.Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "custom.classloader.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CustomClassloaderVisitor();
  }

  private static class CustomClassloaderVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (!InheritanceUtil.isInheritor(aClass, "java.lang.ClassLoader")) {
        return;
      }
      if ("java.lang.ClassLoader".equals(aClass.getQualifiedName())) {
        return;
      }
      registerClassError(aClass);
    }
  }
}