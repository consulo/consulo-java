/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
public class AssignmentToSuperclassFieldInspection extends BaseInspection {

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.assignmentToSuperclassFieldDisplayName().get();
  }

  @Nonnull
  @Override
  @RequiredReadAction
  protected String buildErrorString(Object... infos) {
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) infos[0];
    final PsiClass superclass = (PsiClass) infos[1];
    return InspectionGadgetsLocalize.assignmentToSuperclassFieldProblemDescriptor(
        referenceExpression.getReferenceName(),
        superclass.getName()
    ).get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToSuperclassFieldVisitor();
  }

  private static class AssignmentToSuperclassFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression lhs = expression.getLExpression();
      checkSuperclassField(lhs);
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      final PsiExpression operand = expression.getOperand();
      checkSuperclassField(operand);
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      super.visitPostfixExpression(expression);
      final PsiExpression operand = expression.getOperand();
      checkSuperclassField(operand);
    }

    private void checkSuperclassField(PsiExpression expression) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class);
      if (method == null || !method.isConstructor()) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
      final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
      if (qualifierExpression != null &&
          !(qualifierExpression instanceof PsiThisExpression) && !(qualifierExpression instanceof PsiSuperExpression)) {
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField) target;
      final PsiClass fieldClass = field.getContainingClass();
      if (fieldClass == null) {
        return;
      }
      final PsiClass assignmentClass = method.getContainingClass();
      final String name = fieldClass.getQualifiedName();
      if (name == null || !InheritanceUtil.isInheritor(assignmentClass, true, name)) {
        return;
      }
      registerError(expression, referenceExpression, fieldClass);
    }
  }
}
