/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class MethodNameSameAsClassNameInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.methodNameSameAsClassNameDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.methodNameSameAsClassNameProblemDescriptor().get();
  }

  @Override
  @Nonnull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    Boolean onTheFly = (Boolean)infos[0];
    if (onTheFly.booleanValue()) {
      return new InspectionGadgetsFix[]{
        new RenameFix(), new MethodNameSameAsClassNameFix()};
    }
    else {
      return new InspectionGadgetsFix[]{
        new MethodNameSameAsClassNameFix()
      };
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class MethodNameSameAsClassNameFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.makeMethodCtrQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) {
        return;
      }
      PsiMethod method = (PsiMethod)parent;
      PsiTypeElement returnTypeElement =
        method.getReturnTypeElement();
      if (returnTypeElement == null) {
        return;
      }
      returnTypeElement.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodNameSameAsClassNameVisitor();
  }

  private static class MethodNameSameAsClassNameVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      // no call to super, so it doesn't drill down into inner classes
      if (method.isConstructor()) {
        return;
      }
      String methodName = method.getName();
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      String className = containingClass.getName();
      if (className == null) {
        return;
      }
      if (!methodName.equals(className)) {
        return;
      }
      registerMethodError(method, Boolean.valueOf(isOnTheFly()));
    }
  }
}