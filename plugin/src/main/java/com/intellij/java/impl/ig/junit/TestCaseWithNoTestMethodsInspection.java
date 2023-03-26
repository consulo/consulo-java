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
package com.intellij.java.impl.ig.junit;

import javax.swing.JComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;

@ExtensionImpl
public class TestCaseWithNoTestMethodsInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSupers = false;

  @Override
  @Nonnull
  public String getID() {
    return "JUnitTestCaseWithNoTests";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "test.case.with.no.test.methods.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "test.case.with.no.test.methods.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "test.case.with.no.test.methods.option"), this,
      "ignoreSupers");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseWithNoTestMethodsVisitor();
  }

  private class TestCaseWithNoTestMethodsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (aClass.isInterface()
          || aClass.isEnum()
          || aClass.isAnnotationType()
          || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (!InheritanceUtil.isInheritor(aClass,
                                       "junit.framework.TestCase")) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (TestUtils.isJUnitTestMethod(method)) {
          return;
        }
      }
      if (ignoreSupers) {
        final PsiClass superClass = aClass.getSuperClass();
        if (superClass != null) {
          final PsiMethod[] superMethods = superClass.getMethods();
          for (PsiMethod superMethod : superMethods) {
            if (TestUtils.isJUnitTestMethod(superMethod)) {
              return;
            }
          }
        }
      }
      registerClassError(aClass);
    }
  }
}