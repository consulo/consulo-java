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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ParameterHidingMemberVariableInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreInvisibleFields = true;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreStaticMethodParametersHidingInstanceFields = false;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreForConstructors = false;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreForPropertySetters = false;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreForAbstractMethods = false;

  @Nonnull
  public String getID() {
    return "ParameterHidesMemberVariable";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.parameterHidesMemberVariableDisplayName().get();
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsLocalize.parameterHidesMemberVariableProblemDescriptor(aClass.getName()).get();
  }

  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.parameterHidesMemberVariableIgnoreSettersOption().get(),
      "m_ignoreForPropertySetters"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.parameterHidesMemberVariableIgnoreSuperclassOption().get(),
      "m_ignoreInvisibleFields");
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.parameterHidesMemberVariableIgnoreConstructorsOption().get(),
      "m_ignoreForConstructors"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.parameterHidesMemberVariableIgnoreAbstractMethodsOption().get(),
      "m_ignoreForAbstractMethods"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.parameterHidesMemberVariableIgnoreStaticParametersOption().get(),
      "m_ignoreStaticMethodParametersHidingInstanceFields"
    );
    return optionsPanel;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ParameterHidingMemberVariableVisitor();
  }

  private class ParameterHidingMemberVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitParameter(@Nonnull PsiParameter variable) {
      super.visitParameter(variable);
      final PsiElement declarationScope = variable.getDeclarationScope();
      if (!(declarationScope instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)declarationScope;
      if (m_ignoreForConstructors && method.isConstructor()) {
        return;
      }
      if (m_ignoreForAbstractMethods) {
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          return;
        }
      }
      if (m_ignoreForPropertySetters) {
        final String methodName = method.getName();
        final PsiType returnType = method.getReturnType();
        if (methodName.startsWith(HardcodedMethodConstants.SET) && PsiType.VOID.equals(returnType)) {
          return;
        }
      }
      final PsiClass aClass = checkFieldName(variable, method);
      if (aClass ==  null) {
        return;
      }
      registerVariableError(variable, aClass);
    }

    @Nullable
    private PsiClass checkFieldName(PsiVariable variable, PsiMethod method) {
      final String variableName = variable.getName();
      if (variableName == null) {
        return null;
      }
      PsiClass aClass = ClassUtils.getContainingClass(variable);
      while (aClass != null) {
        final PsiField[] fields = aClass.getAllFields();
        for (PsiField field : fields) {
          final String fieldName = field.getName();
          if (!variableName.equals(fieldName)) {
            continue;
          }
          if (m_ignoreStaticMethodParametersHidingInstanceFields && !field.hasModifierProperty(PsiModifier.STATIC) &&
              method.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          if (!m_ignoreInvisibleFields || ClassUtils.isFieldVisible(field, aClass)) {
            return aClass;
          }
        }
        aClass = ClassUtils.getContainingClass(aClass);
      }
      return null;
    }
  }
}