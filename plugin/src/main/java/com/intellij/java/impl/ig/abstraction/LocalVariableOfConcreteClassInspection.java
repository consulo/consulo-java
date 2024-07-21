/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiNamedElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class LocalVariableOfConcreteClassInspection
  extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAbstractClasses = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.localVariableOfConcreteClassDisplayName().get();
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public String buildErrorString(Object... arg) {
    final PsiNamedElement variable = (PsiNamedElement)arg[0];
    final String name = variable.getName();
    return InspectionGadgetsLocalize.localVariableOfConcreteClassProblemDescriptor(name).get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.localVariableOfConcreteClassOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreAbstractClasses");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LocalVariableOfConcreteClassVisitor();
  }

  private class LocalVariableOfConcreteClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(
      @Nonnull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (!ConcreteClassUtil.typeIsConcreteClass(typeElement,
                                                 ignoreAbstractClasses)) {
        return;
      }
      registerError(typeElement, variable);
    }
  }
}