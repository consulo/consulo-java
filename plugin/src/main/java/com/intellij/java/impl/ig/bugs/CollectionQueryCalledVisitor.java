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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.Set;

class CollectionQueryCalledVisitor extends JavaRecursiveElementVisitor {

  @NonNls private final Set<String> queryNames;

  private boolean queried = false;
  private final PsiVariable variable;

  CollectionQueryCalledVisitor(PsiVariable variable, Set<String> queryNames) {
    this.variable = variable;
    this.queryNames = queryNames;
  }

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (!queried) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitForeachStatement(
    @Nonnull PsiForeachStatement statement) {
    if (queried) {
      return;
    }
    super.visitForeachStatement(statement);
    PsiExpression qualifier = statement.getIteratedValue();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return;
    }
    PsiReference referenceExpression = (PsiReference)qualifier;
    PsiElement referent = referenceExpression.resolve();
    if (referent == null) {
      return;
    }
    if (!referent.equals(variable)) {
      return;
    }
    queried = true;
  }

  @Override
  public void visitMethodCallExpression(
    @Nonnull PsiMethodCallExpression call) {
    if (queried) {
      return;
    }
    super.visitMethodCallExpression(call);
    PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    boolean isStatement =
      call.getParent() instanceof PsiExpressionStatement;
    if (isStatement) {
      String methodName = methodExpression.getReferenceName();
      if (methodName == null) {
        return;
      }
      if (!queryNames.contains(methodName)) {
        boolean found = false;
        for (String queryName : queryNames) {
          if (methodName.startsWith(queryName)) {
            found = true;
            break;
          }
        }
        if (!found) {
          return;
        }
      }
    }
    PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    checkQualifier(qualifier);
  }

  private void checkQualifier(PsiExpression expression) {
    if (queried) {
      return;
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      PsiElement referent = referenceExpression.resolve();
      if (referent == null) {
        return;
      }
      if (referent.equals(variable)) {
        queried = true;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)expression;
      checkQualifier(parenthesizedExpression.getExpression());
    }
    else if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditionalExpression =
        (PsiConditionalExpression)expression;
      PsiExpression thenExpression =
        conditionalExpression.getThenExpression();
      checkQualifier(thenExpression);
      PsiExpression elseExpression =
        conditionalExpression.getElseExpression();
      checkQualifier(elseExpression);
    }
  }

  public boolean isQueried() {
    return queried;
  }
}
