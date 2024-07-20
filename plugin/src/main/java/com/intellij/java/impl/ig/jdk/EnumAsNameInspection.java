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
package com.intellij.java.impl.ig.jdk;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class EnumAsNameInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "EnumAsIdentifier";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.useEnumAsIdentifierDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.useEnumAsIdentifierProblemDescriptor().get();
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
  public BaseInspectionVisitor buildVisitor() {
    return new EnumAsNameVisitor();
  }

  private static class EnumAsNameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@Nonnull PsiVariable variable) {
      super.visitVariable(variable);
      final String variableName = variable.getName();
      if (!PsiKeyword.ENUM.equals(variableName)) {
        return;
      }
      registerVariableError(variable);
    }

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      final String name = method.getName();
      if (!PsiKeyword.ENUM.equals(name)) {
        return;
      }
      registerMethodError(method);
    }

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      //note: no call to super, to avoid drill-down
      final String name = aClass.getName();
      if (!PsiKeyword.ENUM.equals(name)) {
        return;
      }
      final PsiTypeParameterList params = aClass.getTypeParameterList();
      if (params != null) {
        params.accept(this);
      }
      registerClassError(aClass);
    }

    @Override
    public void visitTypeParameter(PsiTypeParameter parameter) {
      super.visitTypeParameter(parameter);
      final String name = parameter.getName();
      if (!PsiKeyword.ENUM.equals(name)) {
        return;
      }
      registerTypeParameterError(parameter);
    }
  }
}