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
package com.intellij.java.impl.ig.portability;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class NativeMethodsInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "NativeMethod";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("native.method.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "native.method.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NativeMethodVisitor();
  }

  private static class NativeMethodVisitor extends BaseInspectionVisitor {


    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (!method.hasModifierProperty(PsiModifier.NATIVE)) {
        return;
      }
      registerModifierError(PsiModifier.NATIVE, method);
    }
  }
}
