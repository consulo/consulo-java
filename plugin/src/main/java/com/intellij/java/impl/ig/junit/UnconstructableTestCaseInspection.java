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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;

import javax.annotation.Nonnull;

@ExtensionImpl
public class UnconstructableTestCaseInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "UnconstructableJUnitTestCase";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unconstructable.test.case.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unconstructable.test.case.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnconstructableTestCaseVisitor();
  }

  private static class UnconstructableTestCaseVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isEnum() ||
          aClass.isAnnotationType() ||
          aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (!InheritanceUtil.isInheritor(aClass,
                                       "junit.framework.TestCase")) {
        return;
      }
      final PsiMethod[] constructors = aClass.getConstructors();
      boolean hasStringConstructor = false;
      boolean hasNoArgConstructor = false;
      boolean hasConstructor = false;
      for (final PsiMethod constructor : constructors) {
        hasConstructor = true;
        if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
          continue;
        }
        final PsiParameterList parameterList =
          constructor.getParameterList();
        final int parametersCount = parameterList.getParametersCount();
        if (parametersCount == 0) {
          hasNoArgConstructor = true;
        }
        if (parametersCount == 1) {
          final PsiParameter[] parameters =
            parameterList.getParameters();
          final PsiType type = parameters[0].getType();
          if (TypeUtils.typeEquals(JavaClassNames.JAVA_LANG_STRING,
                                   type)) {
            hasStringConstructor = true;
          }
        }
      }
      if (!hasConstructor) {
        return;
      }
      if (hasNoArgConstructor || hasStringConstructor) {
        return;
      }
      registerClassError(aClass);
    }
  }
}