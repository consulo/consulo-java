/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class WaitWithoutCorrespondingNotifyInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.waitWithoutCorrespondingNotifyDisplayName().get();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.waitWithoutCorrespondingNotifyProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new WaitWithoutCorrespondingNotifyVisitor();
  }

  private static class WaitWithoutCorrespondingNotifyVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ThreadingUtils.isWaitCall(expression)) {
        return;
      }

      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)qualifier).resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      final PsiClass fieldClass = field.getContainingClass();
      if (fieldClass == null) {
        return;
      }
      if (!PsiTreeUtil.isAncestor(fieldClass, expression, true)) {
        return;
      }
      if (containsNotifyCall(fieldClass, field)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean containsNotifyCall(
      PsiClass fieldClass, PsiField field) {
      final ContainsNotifyVisitor visitor =
        new ContainsNotifyVisitor(field);
      fieldClass.accept(visitor);
      return visitor.containsNotify();
    }
  }

  private static class ContainsNotifyVisitor
    extends JavaRecursiveElementVisitor {

    private final PsiField target;
    private boolean containsNotify = false;

    ContainsNotifyVisitor(PsiField target) {
      super();
      this.target = target;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (containsNotify) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ThreadingUtils.isNotifyOrNotifyAllCall(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)qualifier).resolve();
      if (referent == null) {
        return;
      }
      if (!target.equals(referent)) {
        return;
      }
      containsNotify = true;
    }

    public boolean containsNotify() {
      return containsNotify;
    }
  }
}
