/*
 * Copyright 2006-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import javax.annotation.Nonnull;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;

public class BeforeOrAfterIsPublicVoidNoArgInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "BeforeOrAfterWithIncorrectSignature";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "before.or.after.is.public.void.no.arg.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "before.or.after.is.public.void.no.arg.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BeforeOrAfterIsPublicVoidNoArgVisitor();
  }

  private static class BeforeOrAfterIsPublicVoidNoArgVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      //note: no call to super;
      if (!TestUtils.isJUnit4BeforeOrAfterMethod(method)) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      final PsiClass targetClass = method.getContainingClass();
      if (targetClass == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        registerMethodError(method);
      }
      else if (!returnType.equals(PsiType.VOID)) {
        registerMethodError(method);
      }
      else if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        registerMethodError(method);
      }
      else if (method.hasModifierProperty(PsiModifier.STATIC)) {
        registerMethodError(method);
      }
    }
  }
}
