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

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class StaticMethodNamingConventionInspection
  extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.staticMethodNamingConventionDisplayName().get();
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final String methodName = (String)infos[0];
    if (methodName.length() < getMinLength()) {
      return InspectionGadgetsBundle.message(
        "static.method.naming.convention.problem.descriptor.short");
    }
    else if (methodName.length() > getMaxLength()) {
      return InspectionGadgetsBundle.message(
        "static.method.naming.convention.problem.descriptor.long");
    }
    return InspectionGadgetsBundle.message(
      "static.method.naming.convention.problem.descriptor.regex.mismatch",
      getRegex());
  }

  protected String getDefaultRegex() {
    return "[a-z][A-Za-z\\d]*";
  }

  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final String name = method.getName();
      if (isValid(name)) {
        return;
      }
      registerMethodError(method, name);
    }
  }
}
