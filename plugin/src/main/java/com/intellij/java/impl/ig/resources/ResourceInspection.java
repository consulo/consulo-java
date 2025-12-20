/*
 * Copyright 2008-2011 Bas Leijdekkers
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

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;

import jakarta.annotation.Nullable;

public abstract class ResourceInspection extends BaseInspection {

  @Nullable
  protected static PsiVariable getVariable(
    @Nonnull PsiElement element) {
    if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment =
        (PsiAssignmentExpression)element;
      PsiExpression lhs = assignment.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)lhs;
      PsiElement referent = referenceExpression.resolve();
      if (!(referent instanceof PsiVariable)) {
        return null;
      }
      return (PsiVariable)referent;
    }
    else if (element instanceof PsiVariable) {
      return (PsiVariable)element;
    }
    else {
      return null;
    }
  }

  protected static PsiElement getExpressionParent(PsiExpression expression) {
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression ||
           parent instanceof PsiTypeCastExpression) {
      parent = parent.getParent();
    }
    return parent;
  }

  protected static boolean isSafelyClosed(@Nullable PsiVariable variable, PsiElement context, boolean insideTryAllowed) {
    if (variable == null) {
      return false;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(context, PsiStatement.class);
    if (statement == null) {
      return false;
    }
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (insideTryAllowed) {
      PsiStatement parentStatement = PsiTreeUtil.getParentOfType(statement, PsiStatement.class);
      while (parentStatement != null && !(parentStatement instanceof PsiTryStatement)) {
        parentStatement = PsiTreeUtil.getParentOfType(parentStatement, PsiStatement.class);
      }
      if (parentStatement != null) {
        PsiTryStatement tryStatement = (PsiTryStatement)parentStatement;
        if (isResourceClosedInFinally(tryStatement, variable)) {
          return true;
        }
      }
    }
    while (nextStatement == null) {
      statement = PsiTreeUtil.getParentOfType(statement, PsiStatement.class, true);
      if (statement == null) {
        return false;
      }
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        statement = (PsiStatement)parent;
      }
      nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    }
    if (!(nextStatement instanceof PsiTryStatement)) {
      // exception in next statement can prevent closing of the resource
      return isResourceClose(nextStatement, variable);
    }
    PsiTryStatement tryStatement = (PsiTryStatement)nextStatement;
    if (isResourceClosedInFinally(tryStatement, variable)) {
      return true;
    }
    return isResourceClose(nextStatement, variable);
  }

  protected static boolean isResourceClosedInFinally(
    @Nonnull PsiTryStatement tryStatement,
    @Nonnull PsiVariable variable) {
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock == null) {
      return false;
    }
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return false;
    }
    CloseVisitor visitor = new CloseVisitor(variable);
    finallyBlock.accept(visitor);
    return visitor.containsClose();
  }

  private static boolean isResourceClose(PsiStatement statement,
                                         PsiVariable variable) {
    if (statement instanceof PsiExpressionStatement) {
      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)statement;
      PsiExpression expression =
        expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expression;
      return isResourceClose(methodCallExpression, variable);
    }
    else if (statement instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)statement;
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) {
        return false;
      }
      PsiStatement[] innerStatements = tryBlock.getStatements();
      if (innerStatements.length == 0) {
        return false;
      }
      if (isResourceClose(innerStatements[0], variable)) {
        return true;
      }
    }
    else if (statement instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)statement;
      PsiExpression condition = ifStatement.getCondition();
      if (!(condition instanceof PsiBinaryExpression)) {
        return false;
      }
      PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)condition;
      IElementType tokenType =
        binaryExpression.getOperationTokenType();
      if (JavaTokenType.NE != tokenType) {
        return false;
      }
      PsiExpression lhs = binaryExpression.getLOperand();
      PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return false;
      }
      if (PsiType.NULL.equals(lhs.getType())) {
        if (!(rhs instanceof PsiReferenceExpression)) {
          return false;
        }
        PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)rhs;
        PsiElement target = referenceExpression.resolve();
        if (!variable.equals(target)) {
          return false;
        }
      }
      else if (PsiType.NULL.equals(rhs.getType())) {
        if (!(lhs instanceof PsiReferenceExpression)) {
          return false;
        }
        PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)lhs;
        PsiElement target = referenceExpression.resolve();
        if (!variable.equals(target)) {
          return false;
        }
      }
      PsiStatement thenBranch = ifStatement.getThenBranch();
      return isResourceClose(thenBranch, variable);
    }
    else if (statement instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement =
        (PsiBlockStatement)statement;
      PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      PsiStatement[] statements = codeBlock.getStatements();
      return statements.length != 0 &&
             isResourceClose(statements[0], variable);
    }
    return false;
  }

  protected static boolean isResourceEscapedFromMethod(
    PsiVariable boundVariable, PsiElement context) {
    // poor man dataflow
    PsiMethod method =
      PsiTreeUtil.getParentOfType(context, PsiMethod.class, true,
                                  PsiMember.class);
    if (method == null) {
      return false;
    }
    PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }
    EscapeVisitor visitor = new EscapeVisitor(boundVariable);
    body.accept(visitor);
    return visitor.isEscaped();
  }

  protected static boolean isResourceClose(PsiMethodCallExpression call,
                                           PsiVariable resource) {
    PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    String methodName = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
      return false;
    }
    PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiReference reference = (PsiReference)qualifier;
    PsiElement referent = reference.resolve();
    return referent != null && referent.equals(resource);
  }

  private static class CloseVisitor extends JavaRecursiveElementVisitor {

    private boolean containsClose = false;
    private final PsiVariable resource;
    private final String resourceName;

    private CloseVisitor(PsiVariable resource) {
      this.resource = resource;
      this.resourceName = resource.getName();
    }

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (!containsClose) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression call) {
      if (containsClose) {
        return;
      }
      super.visitMethodCallExpression(call);
      if (!isResourceClose(call, resource)) {
        return;
      }
      containsClose = true;
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression referenceExpression) {
      // check if resource is closed in a method like IOUtils.silentClose()
      super.visitReferenceExpression(referenceExpression);
      if (containsClose) {
        return;
      }
      String text = referenceExpression.getText();
      if (text == null || !text.equals(resourceName)) {
        return;
      }
      PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      PsiExpressionList argumentList = (PsiExpressionList)parent;
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)grandParent;
      PsiElement target = referenceExpression.resolve();
      if (target == null || !target.equals(resource)) {
        return;
      }
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiCodeBlock codeBlock = method.getBody();
      if (codeBlock == null) {
        return;
      }
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length != 1) {
        return;
      }
      PsiParameter parameter = parameters[0];
      PsiStatement[] statements = codeBlock.getStatements();
      for (PsiStatement statement : statements) {
        if (isResourceClose(statement, parameter)) {
          containsClose = true;
          return;
        }
      }
    }

    public boolean containsClose() {
      return containsClose;
    }
  }

  private static class EscapeVisitor extends JavaRecursiveElementVisitor {

    private final PsiVariable boundVariable;
    private boolean escaped = false;

    public EscapeVisitor(PsiVariable boundVariable) {
      this.boundVariable = boundVariable;
    }

    @Override
    public void visitAnonymousClass(PsiAnonymousClass aClass) {
    }

    @Override
    public void visitElement(PsiElement element) {
      if (escaped) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReturnStatement(
      PsiReturnStatement statement) {
      PsiExpression value = statement.getReturnValue();
      value = PsiUtil.deparenthesizeExpression(value);
      if (value instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)value;
        PsiElement target = referenceExpression.resolve();
        if (target != null && target.equals(boundVariable)) {
          escaped = true;
        }
      }
    }

    public boolean isEscaped() {
      return escaped;
    }
  }
}
