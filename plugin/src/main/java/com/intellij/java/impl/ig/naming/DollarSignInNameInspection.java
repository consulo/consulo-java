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

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiVariable;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;

public class DollarSignInNameInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "dollar.sign.in.name.display.name");
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "dollar.sign.in.name.problem.descriptor");
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new DollarSignInNameVisitor();
  }

  private static class DollarSignInNameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@Nonnull PsiVariable variable) {
      super.visitVariable(variable);
      final String name = variable.getName();
      if (name == null) {
        return;
      }
      if (name.indexOf((int)'$') < 0) {
        return;
      }
      registerVariableError(variable);
    }

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      final String name = method.getName();
      if (name.indexOf((int)'$') < 0) {
        return;
      }
      registerMethodError(method);
    }

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      //note: no call to super, to avoid drill-down
      final String name = aClass.getName();
      if (name == null) {
        return;
      }
      if (name.indexOf((int)'$') < 0) {
        return;
      }
      registerClassError(aClass);
    }
  }
}