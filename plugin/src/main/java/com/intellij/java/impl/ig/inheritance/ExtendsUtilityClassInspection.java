/*
 * Copyright 2006-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.inheritance;

import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.impl.ig.psiutils.UtilityClassUtil;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;

@ExtensionImpl
public class ExtendsUtilityClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreUtilityClasses = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("class.extends.utility.class.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    final PsiClass superClass = (PsiClass)infos[0];
    final String superClassName = superClass.getName();
    return InspectionGadgetsBundle.message("class.extends.utility.class.problem.descriptor", superClassName);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("class.extends.utility.class.ignore.utility.class.option"),
                                          this, "ignoreUtilityClasses");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassExtendsUtilityClassVisitor();
  }

  private class ClassExtendsUtilityClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      final PsiClass superClass = aClass.getSuperClass();
      if (superClass == null) {
        return;
      }
      if (superClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(superClass)) {
        return;
      }
      if (ignoreUtilityClasses && UtilityClassUtil.isUtilityClass(aClass, false)) {
        return;
      }
      registerClassError(aClass, superClass);
    }
  }
}