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
package com.intellij.java.impl.ig.methodmetrics;

import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

public abstract class CyclomaticComplexityInspection extends MethodMetricInspection {

  @Nonnull
  public String getID() {
    return "OverlyComplexMethod";
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.cyclomaticComplexityDisplayName();
  }

  protected int getDefaultLimit() {
    return 10;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsLocalize.methodComplexityLimitOption().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    Integer complexity = (Integer)infos[0];
    return InspectionGadgetsLocalize.cyclomaticComplexityProblemDescriptor(complexity).get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MethodComplexityVisitor();
  }

  private class MethodComplexityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      CyclomaticComplexityVisitor visitor =
        new CyclomaticComplexityVisitor();
      method.accept(visitor);
      int complexity = visitor.getComplexity();
      if (complexity <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(complexity));
    }
  }
}