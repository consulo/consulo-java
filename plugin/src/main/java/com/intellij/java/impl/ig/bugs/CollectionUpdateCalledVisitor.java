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
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import java.util.Set;

class CollectionUpdateCalledVisitor extends JavaRecursiveElementVisitor {

  @NonNls private final Set<String> updateNames;

  private boolean updated = false;
  private final PsiVariable variable;

  CollectionUpdateCalledVisitor(@Nullable PsiVariable variable, Set<String> updateNames) {
    this.variable = variable;
    this.updateNames = updateNames;
  }

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (!updated) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    super.visitMethodReferenceExpression(expression);
    if (updated) return;
    String methodName = expression.getReferenceName();
    if (checkMethodName(methodName)) return;
    checkQualifier(expression.getQualifierExpression());
  }

  @Override
  public void visitMethodCallExpression(
    @Nonnull PsiMethodCallExpression call) {
    super.visitMethodCallExpression(call);
    if (updated) {
      return;
    }
    PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    String methodName = methodExpression.getReferenceName();
    if (checkMethodName(methodName)) return;
    PsiExpression qualifier = methodExpression.getQualifierExpression();
    checkQualifier(qualifier);
  }

  private boolean checkMethodName(String methodName) {
    if (methodName == null) {
      return true;
    }
    if (!updateNames.contains(methodName)) {
      boolean found = false;
      for (String updateName : updateNames) {
        if (!methodName.startsWith(updateName)) {
          continue;
        }
        found = true;
        break;
      }
      if (!found) {
        return true;
      }
    }
    return false;
  }

  private void checkQualifier(PsiExpression expression) {
    if (updated) {
      return;
    }
    if (variable != null && expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      PsiElement referent = referenceExpression.resolve();
      if (referent == null) {
        return;
      }
      if (referent.equals(variable)) {
        updated = true;
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
    else if (variable == null) {
      if (expression == null || expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression) {
        updated = true;
      }
    }
  }

  public boolean isUpdated() {
    return updated;
  }
}
