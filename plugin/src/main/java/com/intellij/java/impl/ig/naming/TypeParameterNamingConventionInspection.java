/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class TypeParameterNamingConventionInspection
  extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 1;
  private static final int DEFAULT_MAX_LENGTH = 1;

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.typeParameterNamingConventionDisplayName().get();
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final String parameterName = (String)infos[0];
    if (parameterName.length() < getMinLength()) {
      return InspectionGadgetsBundle.message(
        "type.parameter.naming.convention.problem.descriptor.short");
    }
    else if (parameterName.length() > getMaxLength()) {
      return InspectionGadgetsBundle.message(
        "type.parameter.naming.convention.problem.descriptor.long");
    }
    return InspectionGadgetsBundle.message(
      "enumerated.class.naming.convention.problem.descriptor.regex.mismatch",
      getRegex());
  }

  protected String getDefaultRegex() {
    return "[A-Z][A-Za-z\\d]*";
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
    public void visitTypeParameter(PsiTypeParameter parameter) {
      super.visitTypeParameter(parameter);
      final String name = parameter.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      registerError(nameIdentifier, name);
    }
  }
}
