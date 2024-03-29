/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.j2me;

import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import jakarta.annotation.Nonnull;

import javax.swing.JComponent;
import java.util.Set;

@ExtensionImpl
public class FieldRepeatedlyAccessedInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreFinalFields = false;

  @Override
  @Nonnull
  public String getID() {
    return "FieldRepeatedlyAccessedInMethod";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "field.repeatedly.accessed.in.method.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... arg) {
    final String fieldName = ((PsiNamedElement)arg[0]).getName();
    return InspectionGadgetsBundle.message(
      "field.repeatedly.accessed.in.method.problem.descriptor",
      fieldName);
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "field.repeatedly.accessed.in.method.ignore.option"),
                                          this, "m_ignoreFinalFields");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldRepeatedlyAccessedVisitor();
  }

  private class FieldRepeatedlyAccessedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      final VariableAccessVisitor visitor = new VariableAccessVisitor();
      method.accept(visitor);
      final Set<PsiField> fields = visitor.getOveraccessedFields();
      for (PsiField field : fields) {
        if (ExpressionUtils.isConstant(field)) {
          continue;
        }
        if (m_ignoreFinalFields &&
            field.hasModifierProperty(PsiModifier.FINAL)) {
          continue;
        }
        registerError(nameIdentifier, field);
      }
    }
  }
}