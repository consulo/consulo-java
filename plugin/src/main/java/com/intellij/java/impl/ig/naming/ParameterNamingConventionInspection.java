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

import com.intellij.java.language.psi.PsiCatchSection;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiForeachStatement;
import com.intellij.java.language.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ParameterNamingConventionInspection extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 1;
  private static final int DEFAULT_MAX_LENGTH = 20;

  @Nonnull
  public String getID() {
    return "MethodParameterNamingConvention";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "parameter.naming.convention.display.name");
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final String parametername = (String)infos[0];
    if (parametername.length() < getMinLength()) {
      return InspectionGadgetsBundle.message(
        "parameter.naming.convention.problem.descriptor.short");
    }
    else if (parametername.length() > getMaxLength()) {
      return InspectionGadgetsBundle.message(
        "parameter.naming.convention.problem.descriptor.long");
    }
    else {
      return InspectionGadgetsBundle.message(
        "parameter.naming.convention.problem.descriptor.regex.mismatch",
        getRegex());
    }
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
    public void visitParameter(@Nonnull PsiParameter variable) {
      final PsiElement scope = variable.getDeclarationScope();
      if (scope instanceof PsiCatchSection ||
          scope instanceof PsiForeachStatement) {
        return;
      }
      final String name = variable.getName();
      if (name == null || isValid(name)) {
        return;
      }
      registerVariableError(variable, name);
    }
  }
}