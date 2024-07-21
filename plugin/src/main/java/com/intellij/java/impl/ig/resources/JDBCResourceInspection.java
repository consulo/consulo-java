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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class JDBCResourceInspection extends ResourceInspection {

  private static final String[] creationMethodClassName =
    {
      "java.sql.Driver",
      "java.sql.DriverManager",
      "javax.sql.DataSource",
      "java.sql.Connection",
      "java.sql.Connection",
      "java.sql.Connection",
      "java.sql.Statement",
      "java.sql.Statement",
      "java.sql.Statement",
    };
  @NonNls
  private static final String[] creationMethodName =
    {
      "connect",
      "getConnection",
      "getConnection",
      "createStatement",
      "prepareStatement",
      "prepareCall",
      "executeQuery",
      "getResultSet",
      "getGeneratedKeys"
    };

  @SuppressWarnings({"StaticCollection"})
  private static final Set<String> creationMethodNameSet =
    new HashSet<String>(9);

  @SuppressWarnings({"PublicField"})
  public boolean insideTryAllowed = false;

  static {
    ContainerUtil.addAll(creationMethodNameSet, creationMethodName);
  }

  @Override
  @Nonnull
  public String getID() {
    return "JDBCResourceOpenedButNotSafelyClosed";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.jdbcResourceOpenedNotClosedDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message(
      "jdbc.resource.opened.not.closed.problem.descriptor", text);
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.allowResourceToBeOpenedInsideATryBlock();
    return new SingleCheckboxOptionsPanel(message.get(), this, "insideTryAllowed");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new JDBCResourceVisitor();
  }

  private class JDBCResourceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isJDBCResourceCreation(expression)) {
        return;
      }
      final PsiElement parent = getExpressionParent(expression);
      if (parent instanceof PsiReturnStatement ||
          parent instanceof PsiResourceVariable) {
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

    private boolean isJDBCResourceCreation(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (name == null) {
        return false;
      }
      if (!creationMethodNameSet.contains(name)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      for (int i = 0; i < creationMethodName.length; i++) {
        if (!name.equals(creationMethodName[i])) {
          continue;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
          return false;
        }
        final String className = containingClass.getQualifiedName();
        if (className == null) {
          return false;
        }
        if (className.equals(creationMethodClassName[i])) {
          return true;
        }
      }
      return false;
    }
  }
}
