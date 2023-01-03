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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import javax.annotation.Nonnull;

public abstract class CyclomaticComplexityInspection extends MethodMetricInspection {

  @Nonnull
  public String getID() {
    return "OverlyComplexMethod";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cyclomatic.complexity.display.name");
  }

  protected int getDefaultLimit() {
    return 10;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message(
      "method.complexity.limit.option");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer complexity = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "cyclomatic.complexity.problem.descriptor", complexity);
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
      final CyclomaticComplexityVisitor visitor =
        new CyclomaticComplexityVisitor();
      method.accept(visitor);
      final int complexity = visitor.getComplexity();
      if (complexity <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(complexity));
    }
  }
}