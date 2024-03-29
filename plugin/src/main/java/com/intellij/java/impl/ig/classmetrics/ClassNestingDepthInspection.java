/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.classmetrics;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;

@ExtensionImpl
public class ClassNestingDepthInspection
  extends ClassMetricInspection {

  private static final int CLASS_NESTING_LIMIT = 1;

  @Nonnull
  public String getID() {
    return "InnerClassTooDeeplyNested";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "inner.class.too.deeply.nested.display.name");
  }

  protected int getDefaultLimit() {
    return CLASS_NESTING_LIMIT;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message(
      "inner.class.too.deeply.nested.nesting.limit.option");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer nestingLevel = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "inner.class.too.deeply.nested.problem.descriptor",
      nestingLevel);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ClassNestingLevel();
  }

  private class ClassNestingLevel extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      // note: no call to super
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final int nestingLevel = getNestingLevel(aClass);
      if (nestingLevel <= getLimit()) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(nestingLevel));
    }

    private int getNestingLevel(PsiClass aClass) {
      PsiElement ancestor = aClass.getParent();
      int nestingLevel = 0;
      while (ancestor != null) {
        if (ancestor instanceof PsiClass) {
          nestingLevel++;
        }
        ancestor = ancestor.getParent();
      }
      return nestingLevel;
    }
  }
}