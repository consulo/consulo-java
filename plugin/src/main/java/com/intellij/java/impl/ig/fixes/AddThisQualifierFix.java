/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

public class AddThisQualifierFix extends InspectionGadgetsFix {

  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.addThisQualifierQuickfix();
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    PsiReferenceExpression expression = (PsiReferenceExpression)descriptor.getPsiElement();
    if (expression.getQualifierExpression() != null) {
      return;
    }
    PsiElement target = expression.resolve();
    if (!(target instanceof PsiMember)) {
      return;
    }
    PsiMember member = (PsiMember)target;
    PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) {
      return;
    }
    PsiClass containingClass = ClassUtils.getContainingClass(expression);
    @NonNls String newExpression;
    if (InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
      newExpression = "this." + expression.getText();
    }
    else {
      containingClass = ClassUtils.getContainingClass(containingClass);
      if (containingClass == null) {
        return;
      }
      while (!InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
        containingClass = ClassUtils.getContainingClass(containingClass);
        if (containingClass == null) {
          return;
        }
      }
      newExpression = containingClass.getQualifiedName() + ".this." + expression.getText();
    }
    replaceExpressionAndShorten(expression, newExpression);
  }
}
