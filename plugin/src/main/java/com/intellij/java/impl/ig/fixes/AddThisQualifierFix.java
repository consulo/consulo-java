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

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

public class AddThisQualifierFix extends InspectionGadgetsFix {

  @Nonnull
  public String getName() {
    return InspectionGadgetsBundle.message("add.this.qualifier.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiReferenceExpression expression = (PsiReferenceExpression)descriptor.getPsiElement();
    if (expression.getQualifierExpression() != null) {
      return;
    }
    final PsiElement target = expression.resolve();
    if (!(target instanceof PsiMember)) {
      return;
    }
    final PsiMember member = (PsiMember)target;
    final PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) {
      return;
    }
    PsiClass containingClass = ClassUtils.getContainingClass(expression);
    @NonNls final String newExpression;
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
