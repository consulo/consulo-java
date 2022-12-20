/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.classlayout;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RemoveModifierFix;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class FinalStaticMethodInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "final.static.method.display.name");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "final.static.method.problem.descriptor");
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new FinalStaticMethodVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[0]);
  }

  private static class FinalStaticMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (!method.hasModifierProperty(PsiModifier.FINAL)
          || !method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerModifierError(PsiModifier.FINAL, method, PsiModifier.FINAL);
    }
  }
}