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
package com.siyeh.ig.style;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;

public class UnnecessaryQualifierForThisInspection
  extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.qualifier.for.this.display.name");
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.qualifier.for.this.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryQualifierForThisVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryQualifierForThisFix();
  }

  private static class UnnecessaryQualifierForThisFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.qualifier.for.this.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement qualifier = descriptor.getPsiElement();
      final PsiThisExpression thisExpression =
        (PsiThisExpression)qualifier.getParent();
      replaceExpression(thisExpression, PsiKeyword.THIS);
    }
  }

  private static class UnnecessaryQualifierForThisVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitThisExpression(
      @Nonnull PsiThisExpression thisExpression) {
      super.visitThisExpression(thisExpression);
      final PsiJavaCodeReferenceElement qualifier =
        thisExpression.getQualifier();
      if (qualifier == null) {
        return;
      }
      final PsiElement referent = qualifier.resolve();
      if (!(referent instanceof PsiClass)) {
        return;
      }
      final PsiClass containingClass =
        ClassUtils.getContainingClass(thisExpression);
      if (containingClass == null) {
        return;
      }
      if (!containingClass.equals(referent)) {
        return;
      }
      registerError(qualifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }
  }
}