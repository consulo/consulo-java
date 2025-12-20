/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class ChannelResourceInspection extends ResourceInspection {

  @SuppressWarnings({"PublicField"})
  public boolean insideTryAllowed = false;

  @Override
  @Nonnull
  public String getID() {
    return "ChannelOpenedButNotSafelyClosed";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.channelOpenedNotClosedDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    PsiExpression expression = (PsiExpression)infos[0];
    PsiType type = expression.getType();
    assert type != null;
    String text = type.getPresentableText();
    return InspectionGadgetsLocalize.channelOpenedNotClosedProblemDescriptor(text).get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.allowResourceToBeOpenedInsideATryBlock();
    return new SingleCheckboxOptionsPanel(message.get(), this, "insideTryAllowed");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ChannelResourceVisitor();
  }

  private class ChannelResourceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isChannelFactoryMethod(expression)) {
        return;
      }
      PsiElement parent = getExpressionParent(expression);
      if (parent instanceof PsiReturnStatement ||
          parent instanceof PsiResourceVariable) {
        return;
      }
      PsiVariable boundVariable = getVariable(parent);
      if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
        return;
      }
      if (isChannelFactoryClosedInFinally(expression)) {
        return;
      }
      if (isResourceEscapedFromMethod(boundVariable, expression)) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean isChannelFactoryClosedInFinally(
      PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifier;
      PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return false;
      }
      PsiVariable variable = (PsiVariable)target;
      PsiTryStatement tryStatement =
        PsiTreeUtil.getParentOfType(expression,
                                    PsiTryStatement.class, true, PsiMember.class);
      if (tryStatement == null) {
        return false;
      }
      while (!isResourceClosedInFinally(tryStatement, variable)) {
        tryStatement =
          PsiTreeUtil.getParentOfType(tryStatement,
                                      PsiTryStatement.class, true, PsiMember.class);
        if (tryStatement == null) {
          return false;
        }
      }
      return true;
    }

    private boolean isChannelFactoryMethod(
      PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.GET_CHANNEL.equals(methodName)) {
        return false;
      }
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                  "java.net.Socket",
                                                  "java.net.DatagramSocket",
                                                  "java.net.ServerSocket",
                                                  "java.io.FileInputStream",
                                                  "java.io.FileOutputStream",
                                                  "java.io.RandomAccessFile",
                                                  "com.sun.corba.se.pept.transport.EventHandler",
                                                  "sun.nio.ch.InheritedChannel") != null;
    }
  }
}
