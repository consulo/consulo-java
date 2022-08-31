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

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiReferenceList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;

public class ThrownExceptionsPerMethodInspection
  extends MethodMetricInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "thrown.exceptions.per.method.display.name");
  }

  @Nonnull
  public String getID() {
    return "MethodWithTooExceptionsDeclared";
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer exceptionCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "thrown.exceptions.per.method.problem.descriptor",
      exceptionCount);
  }

  protected int getDefaultLimit() {
    return 3;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message(
      "thrown.exceptions.per.method.limit.option");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThrownExceptionsPerMethodVisitor();
  }

  private class ThrownExceptionsPerMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiReferenceList throwList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] thrownExceptions =
        throwList.getReferenceElements();
      final int exceptionCount = thrownExceptions.length;
      if (exceptionCount <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(exceptionCount));
    }
  }
}