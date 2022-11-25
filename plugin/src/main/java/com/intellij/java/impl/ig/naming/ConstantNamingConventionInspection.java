/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.language.editor.inspection.ui.CheckBox;

import javax.annotation.Nonnull;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.Collection;

public class ConstantNamingConventionInspection extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @SuppressWarnings({"PublicField"})
  public boolean onlyCheckImmutables = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "constant.naming.convention.display.name");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final String fieldName = (String)infos[0];
    if (fieldName.length() < getMinLength()) {
      return InspectionGadgetsBundle.message("constant.naming.convention.problem.descriptor.short");
    }
    else if (fieldName.length() > getMaxLength()) {
      return InspectionGadgetsBundle.message("constant.naming.convention.problem.descriptor.long");
    }
    return InspectionGadgetsBundle.message("constant.naming.convention.problem.descriptor.regex.mismatch", getRegex());
  }

  @Override
  public Collection<? extends JComponent> createExtraOptions() {
    return Arrays.asList(
      new CheckBox(InspectionGadgetsBundle.message("constant.naming.convention.immutables.option"), this, "onlyCheckImmutables"));
  }

  @Override
  protected String getDefaultRegex() {
    return "[A-Z][A-Z_\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      super.visitField(field);
      if (field instanceof PsiEnumConstant) {
        return;
      }
      if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final String name = field.getName();
      if (name == null) {
        return;
      }
      final PsiType type = field.getType();
      if (onlyCheckImmutables && !ClassUtils.isImmutable(type)) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerFieldError(field, name);
    }
  }
}
