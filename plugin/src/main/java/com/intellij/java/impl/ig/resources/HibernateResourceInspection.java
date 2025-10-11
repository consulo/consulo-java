/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.resources;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class HibernateResourceInspection extends ResourceInspection {

  @SuppressWarnings({"PublicField"})
  public boolean insideTryAllowed = false;

  @Override
  @Nonnull
  public String getID() {
    return "HibernateResourceOpenedButNotSafelyClosed";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.hibernateResourceOpenedNotClosedDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsLocalize.hibernateResourceOpenedNotClosedProblemDescriptor(text).get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.allowResourceToBeOpenedInsideATryBlock();
    return new SingleCheckboxOptionsPanel(message.get(), this, "insideTryAllowed");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new HibernateResourceVisitor();
  }

  private class HibernateResourceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isHibernateFactoryMethod(expression)) {
        return;
      }
      final PsiElement parent = getExpressionParent(expression);
      if (parent instanceof PsiReturnStatement) {
        return;
      }
      final PsiVariable boundVariable = getVariable(parent);
      if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
        return;
      }
      if (isResourceEscapedFromMethod(boundVariable, expression)) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean isHibernateFactoryMethod(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.OPEN_SESSION.equals(methodName)) {
        return false;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                  "org.hibernate.SessionFactory");
    }
  }
}