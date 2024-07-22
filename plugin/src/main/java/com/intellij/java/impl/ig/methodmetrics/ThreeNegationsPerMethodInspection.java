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
package com.intellij.java.impl.ig.methodmetrics;

import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class ThreeNegationsPerMethodInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreInEquals = true;

  @SuppressWarnings("UnusedDeclaration")
  public boolean ignoreInAssert = false;

  @Override
  @Nonnull
  public String getID() {
    return "MethodWithMoreThanThreeNegations";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.threeNegationsPerMethodDisplayName().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsLocalize.threeNegationsPerMethodIgnoreOption().get(), "m_ignoreInEquals");
    panel.addCheckbox(InspectionGadgetsLocalize.threeNegationsPerMethodIgnoreAssertOption().get(), "ignoreInAssert");
    return panel;
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer negationCount = (Integer)infos[0];
    return InspectionGadgetsLocalize.threeNegationsPerMethodProblemDescriptor(negationCount).get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreeNegationsPerMethodVisitor();
  }

  private class ThreeNegationsPerMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final NegationCountVisitor visitor = new NegationCountVisitor(ignoreInAssert);
      method.accept(visitor);
      final int negationCount = visitor.getCount();
      if (negationCount <= 3) {
        return;
      }
      if (m_ignoreInEquals && MethodUtils.isEquals(method)) {
        return;
      }
      registerMethodError(method, Integer.valueOf(negationCount));
    }
  }
}