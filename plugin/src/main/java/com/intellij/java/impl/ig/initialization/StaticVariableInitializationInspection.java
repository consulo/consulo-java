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
package com.intellij.java.impl.ig.initialization;

import com.intellij.java.impl.ig.fixes.MakeInitializerExplicitFix;
import com.intellij.java.impl.ig.psiutils.InitializationUtils;
import com.intellij.java.language.psi.*;
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
public class StaticVariableInitializationInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrimitives = false;

  @Override
  @Nonnull
  public String getID() {
    return "StaticVariableMayNotBeInitialized";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.staticVariableMayNotBeInitializedDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.staticVariableMayNotBeInitializedProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.primitiveFieldsIgnoreOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignorePrimitives");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new MakeInitializerExplicitFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticVariableInitializationVisitor();
  }

  private class StaticVariableInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (field.getInitializer() != null) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isEnum()) {
        return;
      }
      if (m_ignorePrimitives) {
        final PsiType type = field.getType();
        if (ClassUtils.isPrimitive(type)) {
          return;
        }
      }
      final PsiClassInitializer[] initializers =
        containingClass.getInitializers();
      for (final PsiClassInitializer initializer : initializers) {
        if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiCodeBlock body = initializer.getBody();
          if (InitializationUtils.blockAssignsVariableOrFails(body,
                                                              field)) {
            return;
          }
        }
      }
      registerFieldError(field);
    }
  }
}