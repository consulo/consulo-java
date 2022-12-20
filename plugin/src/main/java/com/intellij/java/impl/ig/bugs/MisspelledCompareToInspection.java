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
package com.intellij.java.impl.ig.bugs;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameterList;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class MisspelledCompareToInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "misspelled.compareto.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "misspelled.compareto.problem.descriptor");
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix(HardcodedMethodConstants.COMPARE_TO);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MisspelledCompareToVisitor();
  }

  private static class MisspelledCompareToVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      //note: no call to super
      @NonNls final String methodName = method.getName();
      if (!"compareto".equals(methodName)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      registerMethodError(method);
    }
  }
}