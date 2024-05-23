/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TestUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.extension.Extensions;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.ImplicitUsageProvider;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class InstanceVariableInitializationInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrimitives = false;

  @Nonnull
  public String getID() {
    return "InstanceVariableMayNotBeInitialized";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "instance.variable.may.not.be.initialized.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Boolean junitTestCase = (Boolean)infos[0];
    if (junitTestCase.booleanValue()) {
      return InspectionGadgetsBundle.message(
        "instance.Variable.may.not.be.initialized.problem.descriptor.junit");
    }
    return InspectionGadgetsBundle.message(
      "instance.variable.may.not.be.initialized.problem.descriptor");
  }

  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("primitive.fields.ignore.option"),
      this, "m_ignorePrimitives");
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new MakeInitializerExplicitFix();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new InstanceVariableInitializationVisitor();
  }

  private class InstanceVariableInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      if (field.hasModifierProperty(PsiModifier.STATIC) ||
          field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (field.getInitializer() != null) {
        return;
      }
      if (m_ignorePrimitives) {
        final PsiType fieldType = field.getType();
        if (ClassUtils.isPrimitive(fieldType)) {
          return;
        }
      }
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      final ImplicitUsageProvider[] implicitUsageProviders =
        Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
      for (ImplicitUsageProvider provider : implicitUsageProviders) {
        if (provider.isImplicitWrite(field)) {
          return;
        }
      }
      final boolean isTestClass = TestUtils.isJUnitTestClass(aClass);
      if (isTestClass) {
        if (isInitializedInSetup(field, aClass)) {
          return;
        }
      }
      if (isInitializedInInitializer(field)) {
        return;
      }
      if (InitializationUtils.isInitializedInConstructors(field, aClass)) {
        return;
      }
      if (isTestClass) {
        registerFieldError(field, Boolean.TRUE);
      }
      else {
        registerFieldError(field, Boolean.FALSE);
      }
    }

    private boolean isInitializedInSetup(PsiField field,
                                         PsiClass aClass) {
      final PsiMethod setupMethod = getSetupMethod(aClass);
      return InitializationUtils.methodAssignsVariableOrFails(setupMethod,
                                                              field);
    }

    @Nullable
    private PsiMethod getSetupMethod(@Nonnull PsiClass aClass) {
      final PsiMethod[] methods =
        aClass.findMethodsByName("setUp", false);
      for (PsiMethod method : methods) {
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        final PsiParameterList parameterList =
          method.getParameterList();
        if (parameterList.getParametersCount() != 0) {
          continue;
        }
        if (PsiType.VOID.equals(method.getReturnType())) {
          return method;
        }
      }
      return null;
    }

    private static boolean isInitializedInInitializer(@Nonnull PsiField field) {
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return false;
      }
      if (InitializationUtils.isInitializedInInitializer(field, aClass)) {
        return true;
      }
      final PsiField[] fields = aClass.getFields();
      for (PsiField otherField : fields) {
        if (field.equals(otherField)) {
          continue;
        }
        if (otherField.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        final PsiExpression initializer = otherField.getInitializer();
        if (InitializationUtils.expressionAssignsVariableOrFails(initializer, field)) {
          return true;
        }
      }
      return false;
    }
  }
}