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
package com.intellij.java.impl.ig.encapsulation;

import com.intellij.java.impl.ig.fixes.EncapsulateVariableFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public abstract class UseOfAnotherObjectsPrivateFieldInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSameClass = false;
  @SuppressWarnings({"PublicField"})
  public boolean ignoreEquals = false;

  @Override
  @Nonnull
  public String getID() {
    return "AccessingNonPublicFieldOfAnotherObject";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.accessingNonPublicFieldOfAnotherObjectDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.accessingNonPublicFieldOfAnotherObjectProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsLocalize.ignoreAccessesFromTheSameClass().get(), "ignoreSameClass");
    panel.addCheckbox(InspectionGadgetsLocalize.ignoreAccessesFromEqualsMethod().get(), "ignoreEquals");
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiField field = (PsiField) infos[0];
    return new EncapsulateVariableFix(field.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfAnotherObjectsPrivateFieldVisitor();
  }

  private class UseOfAnotherObjectsPrivateFieldVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(
        @Nonnull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        return;
      }
      if (ignoreEquals) {
        final PsiMethod method =
            PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        if (MethodUtils.isEquals(method)) {
          return;
        }
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField) referent;
      if (ignoreSameClass) {
        final PsiClass parent =
            PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        final PsiClass containingClass = field.getContainingClass();
        if (parent != null && parent.equals(containingClass)) {
          return;
        }
      }
      if (!field.hasModifierProperty(PsiModifier.PRIVATE) &&
          !field.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiElement fieldNameElement =
          expression.getReferenceNameElement();
      if (fieldNameElement == null) {
        return;
      }
      registerError(fieldNameElement, field);
    }
  }
}
