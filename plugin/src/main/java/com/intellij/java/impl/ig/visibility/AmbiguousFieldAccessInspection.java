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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

@ExtensionImpl
public class AmbiguousFieldAccessInspection extends BaseInspection {

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.ambiguousFieldAccessDisplayName().get();
  }

  @Nonnull
  @Override
  @RequiredReadAction
  protected String buildErrorString(Object... infos) {
    final PsiClass fieldClass = (PsiClass)infos[0];
    final PsiVariable variable = (PsiVariable)infos[1];
    if (variable instanceof PsiLocalVariable) {
      return InspectionGadgetsLocalize.ambiguousFieldAccessHidesLocalVariableProblemDescriptor(fieldClass.getName()).get();
    }
    else if (variable instanceof PsiParameter) {
      return InspectionGadgetsLocalize.ambiguousFieldAccessHidesParameterProblemDescriptor(fieldClass.getName()).get();
    }
    else {
      return InspectionGadgetsLocalize.ambiguousFieldAccessHidesFieldProblemDescriptor(fieldClass.getName()).get();
    }
  }

  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AmbiguousMethodCallFix();
  }

  private static class AmbiguousMethodCallFix extends InspectionGadgetsFix {
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.ambiguousFieldAccessQuickfix().get();
    }

    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      final String newExpressionText = "super." + referenceExpression.getText();
      replaceExpression(referenceExpression, newExpressionText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AmbiguousFieldAccessVisitor();
  }

  private static class AmbiguousFieldAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.isQualified()) {
        return;
      }
      PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (target == null) {
        return;
      }
      if (!(target instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)target;
      final PsiClass fieldClass = field.getContainingClass();
      if (fieldClass == null || !containingClass.isInheritor(fieldClass, true)) {
        return;
      }
      final PsiElement parent = containingClass.getParent();
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      final String referenceText = expression.getText();
      final PsiVariable variable = resolveHelper.resolveAccessibleReferencedVariable(referenceText, parent);
      if (variable == null || field == variable) {
        return;
      }
      final PsiElement commonParent = PsiTreeUtil.findCommonParent(variable, containingClass);
      if (commonParent == null) {
        return;
      }
      registerError(expression, fieldClass, variable);
    }
  }
}