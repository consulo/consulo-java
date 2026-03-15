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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;

import javax.swing.*;

@ExtensionImpl
public class JNDIResourceInspection extends ResourceInspection {

  @SuppressWarnings({"PublicField"})
  public boolean insideTryAllowed = false;

  @Override
  public String getID() {
    return "JNDIResourceOpenedButNotSafelyClosed";
  }

  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.jndiResourceOpenedNotClosedDisplayName();
  }

  @Override
  public String buildErrorString(Object... infos) {
    PsiExpression expression = (PsiExpression)infos[0];
    PsiType type = expression.getType();
    assert type != null;
    String text = type.getPresentableText();
    return InspectionGadgetsLocalize.resourceOpenedNotClosedProblemDescriptor(text).get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.allowResourceToBeOpenedInsideATryBlock();
    return new SingleCheckboxOptionsPanel(message.get(), this, "insideTryAllowed");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new JNDIResourceVisitor();
  }

  private class JNDIResourceVisitor extends BaseInspectionVisitor {

    private static final String LIST = "list";
    private static final String LIST_BINDING = "listBindings";
    private static final String GET_ALL = "getAll";

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isJNDIFactoryMethod(expression)) {
        return;
      }
      PsiElement parent = getExpressionParent(expression);
      if (parent instanceof PsiReturnStatement) {
        return;
      }
      PsiVariable boundVariable = getVariable(parent);
      if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
        return;
      }
      if (isResourceEscapedFromMethod(boundVariable, expression)) {
        return;
      }
      registerError(expression, expression);
    }


    @Override
    public void visitNewExpression(
      PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isJNDIResource(expression)) {
        return;
      }
      PsiElement parent = getExpressionParent(expression);
      if (parent instanceof PsiReturnStatement) {
        return;
      }
      PsiVariable boundVariable = getVariable(parent);
      if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
        return;
      }
      if (isResourceEscapedFromMethod(boundVariable, expression)) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean isJNDIResource(PsiNewExpression expression) {
      return TypeUtils.expressionHasTypeOrSubtype(expression,
                                                  "javax.naming.InitialContext");
    }

    private boolean isJNDIFactoryMethod(
      PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (LIST.equals(methodName) || LIST_BINDING.equals(methodName)) {
        PsiExpression qualifier =
          methodExpression.getQualifierExpression();
        if (qualifier == null) {
          return false;
        }
        return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                    "javax.naming.Context");
      }
      else if (GET_ALL.equals(methodName)) {
        PsiExpression qualifier =
          methodExpression.getQualifierExpression();
        if (qualifier == null) {
          return false;
        }
        return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                    "javax.naming.directory.Attribute") ||
               TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                    "javax.naming.directory.Attributes");
      }
      else {
        return false;
      }
    }
  }
}