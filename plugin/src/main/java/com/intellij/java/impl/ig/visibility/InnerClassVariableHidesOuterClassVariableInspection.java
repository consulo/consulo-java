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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class InnerClassVariableHidesOuterClassVariableInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreInvisibleFields = true;

  @Nonnull
  public String getID() {
    return "InnerClassFieldHidesOuterClassField";
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.innerClassFieldHidesOuterDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.innerClassFieldHidesOuterProblemDescriptor().get();
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.innerClassFieldHidesOuterIgnoreOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreInvisibleFields");
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassVariableHidesOuterClassVariableVisitor();
  }

  private class InnerClassVariableHidesOuterClassVariableVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      String fieldName = field.getName();
      if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(fieldName)) {
        return;    //special case
      }
      boolean reportStaticsOnly = false;
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
        reportStaticsOnly = true;
      }
      PsiClass ancestorClass =
        ClassUtils.getContainingClass(aClass);
      while (ancestorClass != null) {
        PsiField ancestorField =
          ancestorClass.findFieldByName(fieldName, false);
        if (ancestorField != null) {
          if (!m_ignoreInvisibleFields ||
              !reportStaticsOnly ||
              field.hasModifierProperty(PsiModifier.STATIC)) {
            registerFieldError(field);
          }
        }
        ancestorClass = ClassUtils.getContainingClass(ancestorClass);
      }
    }
  }
}