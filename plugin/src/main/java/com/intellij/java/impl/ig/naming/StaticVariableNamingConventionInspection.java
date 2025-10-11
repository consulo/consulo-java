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
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

@ExtensionImpl
public class StaticVariableNamingConventionInspection
  extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @SuppressWarnings({"PublicField"})
  public boolean checkMutableFinals = false;

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.staticVariableNamingConventionDisplayName();
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
      return InspectionGadgetsLocalize.staticVariableNamingConventionProblemDescriptorShort().get();
    }
    else if (fieldName.length() > getMaxLength()) {
      return InspectionGadgetsLocalize.staticVariableNamingConventionProblemDescriptorLong().get();
    }
    return InspectionGadgetsLocalize.staticVariableNamingConventionProblemDescriptorRegexMismatch(getRegex()).get();
  }

  @Override
  protected String getDefaultRegex() {
    return "s_[a-z][A-Za-z\\d]*";
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
  public Collection<? extends JComponent> createExtraOptions() {
    LocalizeValue message = InspectionGadgetsLocalize.staticVariableNamingConventionMutableOption();
    return Arrays.asList(new CheckBox(message.get(), this, "checkMutableFinals"));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        if (!checkMutableFinals) {
          return;
        }
        else {
          final PsiType type = field.getType();
          if (ClassUtils.isImmutable(type)) {
            return;
          }
        }
      }
      final String name = field.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerFieldError(field, name);
    }
  }
}