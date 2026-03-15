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

import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.localize.LocalizeValue;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class SetupIsPublicVoidNoArgInspection extends BaseInspection {

  @Override
  public String getID() {
    return "SetUpWithIncorrectSignature";
  }

  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.setupIsPublicVoidNoArgDisplayName();
  }

  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.setupIsPublicVoidNoArgProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SetupIsPublicVoidNoArgVisitor();
  }

  private static class SetupIsPublicVoidNoArgVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      //note: no call to super;
      String methodName = method.getName();
      if (!"setUp".equals(methodName)) {
        return;
      }
      PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      PsiClass targetClass = method.getContainingClass();
      if (targetClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(targetClass,
                                       "junit.framework.TestCase")) {
        return;
      }
      PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0 ||
          !returnType.equals(PsiType.VOID) ||
          !method.hasModifierProperty(PsiModifier.PUBLIC) &&
          !method.hasModifierProperty(PsiModifier.PROTECTED)) {
        registerMethodError(method);
      }
    }
  }
}