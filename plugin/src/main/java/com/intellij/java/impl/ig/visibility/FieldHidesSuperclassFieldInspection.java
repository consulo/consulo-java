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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class FieldHidesSuperclassFieldInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreInvisibleFields = true;

  @Nonnull
  public String getID() {
    return "FieldNameHidesFieldInSuperclass";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "field.name.hides.in.superclass.display.name");
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "field.name.hides.in.superclass.problem.descriptor");
  }

  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "field.name.hides.in.superclass.ignore.option"),
                                          this, "m_ignoreInvisibleFields");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new FieldHidesSuperclassFieldVisitor();
  }

  private class FieldHidesSuperclassFieldVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String fieldName = field.getName();
      if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(fieldName)) {
        return;    //special case
      }
      PsiClass ancestorClass = aClass.getSuperClass();
      final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
      while (ancestorClass != null) {
        if (!visitedClasses.add(ancestorClass)) {
          return;
        }
        final PsiField ancestorField =
          ancestorClass.findFieldByName(fieldName, false);
        if (ancestorField != null) {
          if (!m_ignoreInvisibleFields ||
              ClassUtils.isFieldVisible(ancestorField, aClass)) {
            registerFieldError(field);
            return;
          }
        }
        ancestorClass = ancestorClass.getSuperClass();
      }
    }
  }
}