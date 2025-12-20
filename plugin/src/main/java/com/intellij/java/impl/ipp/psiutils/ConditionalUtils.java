/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.psiutils;

import com.intellij.java.language.psi.*;
import org.jetbrains.annotations.NonNls;

public class ConditionalUtils {

  private ConditionalUtils() {}

  public static PsiStatement stripBraces(PsiStatement branch) {
    if (branch instanceof PsiBlockStatement) {
      PsiBlockStatement block = (PsiBlockStatement)branch;
      PsiCodeBlock codeBlock = block.getCodeBlock();
      PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length == 1) {
        return statements[0];
      }
      else {
        return block;
      }
    }
    else {
      return branch;
    }
  }

  public static boolean isReturn(PsiStatement statement,
                                 @NonNls String value) {
    if (statement == null) {
      return false;
    }
    if (!(statement instanceof PsiReturnStatement)) {
      return false;
    }
    PsiReturnStatement returnStatement =
      (PsiReturnStatement)statement;
    if (returnStatement.getReturnValue() == null) {
      return false;
    }
    PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return false;
    }
    String returnValueText = returnValue.getText();
    return value.equals(returnValueText);
  }

  public static boolean isAssignment(PsiStatement statement,
                                     @NonNls String value) {
    if (statement == null) {
      return false;
    }
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    PsiExpressionStatement expressionStatement =
      (PsiExpressionStatement)statement;
    PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiAssignmentExpression)) {
      return false;
    }
    PsiAssignmentExpression assignment =
      (PsiAssignmentExpression)expression;
    PsiExpression rhs = assignment.getRExpression();
    if (rhs == null) {
      return false;
    }
    String rhsText = rhs.getText();
    return value.equals(rhsText);
  }
}
