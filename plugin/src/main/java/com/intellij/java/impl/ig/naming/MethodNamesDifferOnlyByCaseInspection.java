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
package com.intellij.java.impl.ig.naming;

import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.MethodUtils;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class MethodNamesDifferOnlyByCaseInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreIfMethodIsOverride = true;

  @Override
  @Nonnull
  public String getID() {
    return "MethodNamesDifferingOnlyByCase";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.names.differ.only.by.case.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.names.differ.only.by.case.problem.descriptor", infos[0]);
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("method.names.differ.only.by.case.ignore.override.option"),
                                          this, "ignoreIfMethodIsOverride");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodNamesDifferOnlyByCaseVisitor();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  private class MethodNamesDifferOnlyByCaseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (method.isConstructor()) {
        return;
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      final String methodName = method.getName();
      if (ignoreIfMethodIsOverride && MethodUtils.hasSuper(method)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final PsiMethod[] methods = aClass.getAllMethods();
      for (PsiMethod testMethod : methods) {
        final String testMethodName = testMethod.getName();
        if (!methodName.equals(testMethodName) && methodName.equalsIgnoreCase(testMethodName)) {
          registerError(nameIdentifier, testMethodName);
        }
      }
    }
  }
}