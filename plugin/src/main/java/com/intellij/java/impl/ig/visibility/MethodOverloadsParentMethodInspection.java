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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class MethodOverloadsParentMethodInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean reportIncompatibleParameters = false;

  @Override
  @Nonnull
  public String getID() {
    return "MethodOverloadsMethodOfSuperclass";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.methodOverloadsDisplayName();
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
    return InspectionGadgetsLocalize.methodOverloadsProblemDescriptor().get();
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.methodOverloadsReportIncompatibleOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "reportIncompatibleParameters");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodOverloadsParentMethodVisitor();
  }

  private class MethodOverloadsParentMethodVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (method.getNameIdentifier() == null || method.isConstructor()) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      PsiClass ancestorClass = aClass.getSuperClass();
      final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
      while (ancestorClass != null) {
        if (!visitedClasses.add(ancestorClass)) {
          return;
        }
        if (methodOverloads(method, ancestorClass)) {
          registerMethodError(method);
          return;
        }
        ancestorClass = ancestorClass.getSuperClass();
      }
    }

    private boolean methodOverloads(PsiMethod method, PsiClass ancestorClass) {
      final String methodName = method.getName();
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiMethod[] methods = ancestorClass.findMethodsByName(methodName, false);
      for (final PsiMethod testMethod : methods) {
        if (!testMethod.hasModifierProperty(PsiModifier.PRIVATE) &&
            !testMethod.hasModifierProperty(PsiModifier.STATIC) &&
            !isOverriddenInClass(testMethod, method.getContainingClass())) {
          final PsiParameterList testParameterList = testMethod.getParameterList();
          final PsiParameter[] testParameters = testParameterList.getParameters();
          if (testParameters.length == parameters.length) {
            if (reportIncompatibleParameters || parametersAreCompatible(parameters, testParameters)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private boolean isOverriddenInClass(PsiMethod method, PsiClass aClass) {
      return aClass.findMethodsBySignature(method, false).length > 0;
    }

    private boolean parametersAreCompatible(PsiParameter[] parameters, PsiParameter[] testParameters) {
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        final PsiType parameterType = parameter.getType();
        final PsiParameter testParameter = testParameters[i];
        final PsiType testParameterType = testParameter.getType();
        if (!parameterType.isAssignableFrom(testParameterType) && !testParameterType.isAssignableFrom(parameterType)) {
          return false;
        }
      }
      return true;
    }
  }
}
