/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.cloneable;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CloneDeclaresCloneNotSupportedInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "CloneDoesntDeclareCloneNotSupportedException";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.cloneDoesntDeclareClonenotsupportedexceptionDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.cloneDoesntDeclareClonenotsupportedexceptionProblemDescriptor().get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new CloneDeclaresCloneNotSupportedInspectionFix();
  }

  private static class CloneDeclaresCloneNotSupportedInspectionFix extends InspectionGadgetsFix {

    @Override
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.cloneDoesntDeclareClonenotsupportedexceptionDeclareQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodNameIdentifier.getParent();
      PsiUtil.addException(method, "java.lang.CloneNotSupportedException");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneDeclaresCloneNotSupportedExceptionVisitor();
  }

  private static class CloneDeclaresCloneNotSupportedExceptionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (!CloneUtils.isClone(method)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (MethodUtils.hasInThrows(method, "java.lang.CloneNotSupportedException")) {
        return;
      }
      final MethodSignatureBackedByPsiMethod signature = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (signature == null) {
        return;
      }
      final PsiMethod superMethod = signature.getMethod();
      if (!MethodUtils.hasInThrows(superMethod, "java.lang.CloneNotSupportedException")) {
        return;
      }
      registerMethodError(method);
    }
  }
}