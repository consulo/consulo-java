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
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InitializationUtils {

  private InitializationUtils() {
  }

  public static boolean methodAssignsVariableOrFails(
    @Nullable PsiMethod method, @Nonnull PsiVariable variable) {
    return methodAssignsVariableOrFails(method, variable, false);
  }

  public static boolean expressionAssignsVariableOrFails(
    @Nullable PsiExpression expression, @Nonnull PsiVariable variable) {
    return expressionAssignsVariableOrFails(expression, variable,
                                            new HashSet(), true);
  }

  public static boolean methodAssignsVariableOrFails(
    @Nullable PsiMethod method, @Nonnull PsiVariable variable,
    boolean strict) {
    if (method == null) {
      return false;
    }
    PsiCodeBlock body = method.getBody();
    return body != null && blockAssignsVariableOrFails(body, variable,
                                                       strict);
  }

  public static boolean blockAssignsVariableOrFails(
    @Nullable PsiCodeBlock block, @Nonnull PsiVariable variable) {
    return blockAssignsVariableOrFails(block, variable, false);
  }

  public static boolean blockAssignsVariableOrFails(
    @Nullable PsiCodeBlock block, @Nonnull PsiVariable variable,
    boolean strict) {
    return blockAssignsVariableOrFails(block, variable,
                                       new HashSet<MethodSignature>(), strict);
  }

  private static boolean blockAssignsVariableOrFails(
    @Nullable PsiCodeBlock block, @Nonnull PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods, boolean strict) {
    if (block == null) {
      return false;
    }
    PsiStatement[] statements = block.getStatements();
    int assignmentCount = 0;
    for (PsiStatement statement : statements) {
      if (statementAssignsVariableOrFails(statement, variable,
                                          checkedMethods, strict)) {
        if (strict) {
          assignmentCount++;
        }
        else {
          return true;
        }
      }
    }
    return assignmentCount == 1;
  }

  private static boolean statementAssignsVariableOrFails(
    @Nullable PsiStatement statement, PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods, boolean strict) {
    if (statement == null) {
      return false;
    }
    if (ExceptionUtils.statementThrowsException(statement)) {
      return true;
    }
    if (statement instanceof PsiBreakStatement ||
      statement instanceof PsiContinueStatement ||
      statement instanceof PsiAssertStatement ||
      statement instanceof PsiEmptyStatement ||
      statement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    else if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement =
        (PsiReturnStatement)statement;
      PsiExpression returnValue = returnStatement.getReturnValue();
      return expressionAssignsVariableOrFails(returnValue, variable,
                                              checkedMethods, strict);
    }
    else if (statement instanceof PsiThrowStatement) {
      PsiThrowStatement throwStatement =
        (PsiThrowStatement)statement;
      PsiExpression exception = throwStatement.getException();
      return expressionAssignsVariableOrFails(exception, variable,
                                              checkedMethods, strict);
    }
    else if (statement instanceof PsiExpressionListStatement) {
      PsiExpressionListStatement list =
        (PsiExpressionListStatement)statement;
      PsiExpressionList expressionList = list.getExpressionList();
      PsiExpression[] expressions = expressionList.getExpressions();
      for (PsiExpression expression : expressions) {
        if (expressionAssignsVariableOrFails(expression, variable,
                                             checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (statement instanceof PsiExpressionStatement) {
      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)statement;
      PsiExpression expression =
        expressionStatement.getExpression();
      return expressionAssignsVariableOrFails(expression, variable,
                                              checkedMethods, strict);
    }
    else if (statement instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)statement;
      return declarationStatementAssignsVariableOrFails(
        declarationStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiForStatement) {
      PsiForStatement forStatement = (PsiForStatement)statement;
      return forStatementAssignsVariableOrFails(forStatement,
                                                variable,
                                                checkedMethods, strict);
    }
    else if (statement instanceof PsiForeachStatement) {
      PsiForeachStatement foreachStatement =
        (PsiForeachStatement)statement;
      return foreachStatementAssignsVariableOrFails(variable,
                                                    foreachStatement);
    }
    else if (statement instanceof PsiWhileStatement) {
      PsiWhileStatement whileStatement =
        (PsiWhileStatement)statement;
      return whileStatementAssignsVariableOrFails(whileStatement,
                                                  variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      PsiDoWhileStatement doWhileStatement =
        (PsiDoWhileStatement)statement;
      return doWhileAssignsVariableOrFails(doWhileStatement, variable,
                                           checkedMethods, strict);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)statement;
      PsiCodeBlock body = synchronizedStatement.getBody();
      return blockAssignsVariableOrFails(body, variable,
                                         checkedMethods, strict);
    }
    else if (statement instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement =
        (PsiBlockStatement)statement;
      PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      return blockAssignsVariableOrFails(codeBlock, variable,
                                         checkedMethods, strict);
    }
    else if (statement instanceof PsiLabeledStatement) {
      PsiLabeledStatement labeledStatement =
        (PsiLabeledStatement)statement;
      PsiStatement statementLabeled =
        labeledStatement.getStatement();
      return statementAssignsVariableOrFails(statementLabeled, variable,
                                             checkedMethods, strict);
    }
    else if (statement instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)statement;
      return ifStatementAssignsVariableOrFails(ifStatement, variable,
                                               checkedMethods, strict);
    }
    else if (statement instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)statement;
      return tryStatementAssignsVariableOrFails(tryStatement, variable,
                                                checkedMethods, strict);
    }
    else if (statement instanceof PsiSwitchStatement) {
      PsiSwitchStatement switchStatement =
        (PsiSwitchStatement)statement;
      return switchStatementAssignsVariableOrFails(switchStatement,
                                                   variable, checkedMethods, strict);
    }
    else {
      // unknown statement type
      return false;
    }
  }

  public static boolean switchStatementAssignsVariableOrFails(
    @Nonnull PsiSwitchStatement switchStatement,
    @Nonnull PsiVariable variable,
    boolean strict) {
    return switchStatementAssignsVariableOrFails(switchStatement, variable,
                                                 new HashSet(), strict);
  }

  private static boolean switchStatementAssignsVariableOrFails(
    @Nonnull PsiSwitchStatement switchStatement,
    @Nonnull PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods, boolean strict) {
    PsiExpression expression = switchStatement.getExpression();
    if (expressionAssignsVariableOrFails(expression, variable,
                                         checkedMethods, strict)) {
      return true;
    }
    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return false;
    }
    PsiStatement[] statements = body.getStatements();
    boolean containsDefault = false;
    boolean assigns = false;
    for (int i = 0; i < statements.length; i++) {
      PsiStatement statement = statements[i];
      if (statement instanceof PsiSwitchLabelStatement) {
        PsiSwitchLabelStatement labelStatement
          = (PsiSwitchLabelStatement)statement;
        if (i == statements.length - 1) {
          return false;
        }
        if (labelStatement.isDefaultCase()) {
          containsDefault = true;
        }
        assigns = false;
      }
      else if (statement instanceof PsiBreakStatement) {
        PsiBreakStatement breakStatement
          = (PsiBreakStatement)statement;
        if (breakStatement.getLabelIdentifier() != null) {
          return false;
        }
        if (!assigns) {
          return false;
        }
        assigns = false;
      }
      else {
        assigns |= statementAssignsVariableOrFails(statement, variable,
                                                   checkedMethods, strict);
        if (i == statements.length - 1 && !assigns) {
          return false;
        }
      }
    }
    return containsDefault;
  }

  private static boolean declarationStatementAssignsVariableOrFails(
    PsiDeclarationStatement declarationStatement, PsiVariable variable,
    Set<MethodSignature> checkedMethods, boolean strict) {
    PsiElement[] elements =
      declarationStatement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiVariable) {
        PsiVariable declaredVariable = (PsiVariable)element;
        PsiExpression initializer =
          declaredVariable.getInitializer();
        if (expressionAssignsVariableOrFails(initializer, variable,
                                             checkedMethods, strict)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean tryStatementAssignsVariableOrFails(@Nonnull PsiTryStatement tryStatement, PsiVariable variable,
                                                            @Nonnull Set<MethodSignature> checkedMethods, boolean strict) {
    PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      List<PsiResourceVariable> resourceVariables = resourceList.getResourceVariables();
      for (PsiResourceVariable resourceVariable : resourceVariables) {
        PsiExpression initializer = resourceVariable.getInitializer();
        if (expressionAssignsVariableOrFails(initializer, variable, checkedMethods, strict)) {
          return true;
        }
      }
    }
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    boolean initializedInTryAndCatch = blockAssignsVariableOrFails(tryBlock, variable, checkedMethods, strict);
    PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (PsiCodeBlock catchBlock : catchBlocks) {
      if (strict) {
        initializedInTryAndCatch &= ExceptionUtils.blockThrowsException(catchBlock);
      }
      else {
        initializedInTryAndCatch &= blockAssignsVariableOrFails(catchBlock, variable, checkedMethods, strict);
      }
    }
    if (initializedInTryAndCatch) {
      return true;
    }
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    return blockAssignsVariableOrFails(finallyBlock, variable, checkedMethods, strict);
  }

  private static boolean ifStatementAssignsVariableOrFails(
    @Nonnull PsiIfStatement ifStatement,
    PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods,
    boolean strict) {
    PsiExpression condition = ifStatement.getCondition();
    if (expressionAssignsVariableOrFails(condition, variable,
                                         checkedMethods, strict)) {
      return true;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (BoolUtils.isTrue(condition)) {
      return statementAssignsVariableOrFails(thenBranch, variable,
                                             checkedMethods, strict);
    }
    else if (BoolUtils.isFalse(condition)) {
      return statementAssignsVariableOrFails(elseBranch, variable,
                                             checkedMethods, strict);
    }
    return statementAssignsVariableOrFails(thenBranch, variable,
                                           checkedMethods, strict) &&
      statementAssignsVariableOrFails(elseBranch, variable,
                                      checkedMethods, strict);
  }

  private static boolean doWhileAssignsVariableOrFails(
    @Nonnull PsiDoWhileStatement doWhileStatement,
    PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods,
    boolean strict) {
    PsiExpression condition = doWhileStatement.getCondition();
    PsiStatement body = doWhileStatement.getBody();
    return expressionAssignsVariableOrFails(condition, variable,
                                            checkedMethods, strict) ||
      statementAssignsVariableOrFails(body, variable, checkedMethods,
                                      strict);
  }

  private static boolean whileStatementAssignsVariableOrFails(
    @Nonnull PsiWhileStatement whileStatement, PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods,
    boolean strict) {
    PsiExpression condition = whileStatement.getCondition();
    if (expressionAssignsVariableOrFails(condition, variable,
                                         checkedMethods, strict)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      PsiStatement body = whileStatement.getBody();
      if (statementAssignsVariableOrFails(body, variable, checkedMethods,
                                          strict)) {
        return true;
      }
    }
    return false;
  }

  private static boolean forStatementAssignsVariableOrFails(
    @Nonnull PsiForStatement forStatement, PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods, boolean strict) {
    PsiStatement initialization = forStatement.getInitialization();
    if (statementAssignsVariableOrFails(initialization, variable,
                                        checkedMethods, strict)) {
      return true;
    }
    PsiExpression test = forStatement.getCondition();
    if (expressionAssignsVariableOrFails(test, variable, checkedMethods,
                                         strict)) {
      return true;
    }
    if (BoolUtils.isTrue(test)) {
      PsiStatement body = forStatement.getBody();
      if (statementAssignsVariableOrFails(body, variable, checkedMethods,
                                          strict)) {
        return true;
      }
      PsiStatement update = forStatement.getUpdate();
      if (statementAssignsVariableOrFails(update, variable,
                                          checkedMethods, strict)) {
        return true;
      }
    }
    return false;
  }

  private static boolean foreachStatementAssignsVariableOrFails(PsiVariable field, PsiForeachStatement forStatement) {
    return false;
  }

  private static boolean expressionAssignsVariableOrFails(@Nullable PsiExpression expression, PsiVariable variable,
                                                          @Nonnull Set<MethodSignature> checkedMethods, boolean strict) {
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiThisExpression ||
      expression instanceof PsiLiteralExpression ||
      expression instanceof PsiSuperExpression ||
      expression instanceof PsiClassObjectAccessExpression ||
      expression instanceof PsiReferenceExpression) {
      return false;
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      PsiExpression unparenthesizedExpression = parenthesizedExpression.getExpression();
      return expressionAssignsVariableOrFails(unparenthesizedExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      return methodCallAssignsVariableOrFails(methodCallExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)expression;
      return newExpressionAssignsVariableOrFails(newExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiArrayInitializerExpression) {
      PsiArrayInitializerExpression array = (PsiArrayInitializerExpression)expression;
      PsiExpression[] initializers = array.getInitializers();
      for (PsiExpression initializer : initializers) {
        if (expressionAssignsVariableOrFails(initializer, variable, checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression typeCast = (PsiTypeCastExpression)expression;
      PsiExpression operand = typeCast.getOperand();
      return expressionAssignsVariableOrFails(operand, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression accessExpression = (PsiArrayAccessExpression)expression;
      PsiExpression arrayExpression = accessExpression.getArrayExpression();
      PsiExpression indexExpression = accessExpression.getIndexExpression();
      return expressionAssignsVariableOrFails(arrayExpression, variable, checkedMethods, strict) ||
        expressionAssignsVariableOrFails(indexExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      PsiExpression operand = prefixExpression.getOperand();
      return expressionAssignsVariableOrFails(operand, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiPostfixExpression) {
      PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      PsiExpression operand = postfixExpression.getOperand();
      return expressionAssignsVariableOrFails(operand, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (expressionAssignsVariableOrFails(operand, variable, checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)expression;
      PsiExpression condition = conditional.getCondition();
      if (expressionAssignsVariableOrFails(condition, variable, checkedMethods, strict)) {
        return true;
      }
      PsiExpression thenExpression = conditional.getThenExpression();
      PsiExpression elseExpression = conditional.getElseExpression();
      return expressionAssignsVariableOrFails(thenExpression, variable, checkedMethods, strict) &&
        expressionAssignsVariableOrFails(elseExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      PsiExpression lhs = assignment.getLExpression();
      if (expressionAssignsVariableOrFails(lhs, variable, checkedMethods, strict)) {
        return true;
      }
      PsiExpression rhs = assignment.getRExpression();
      if (expressionAssignsVariableOrFails(rhs, variable, checkedMethods, strict)) {
        return true;
      }
      if (lhs instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReference)lhs).resolve();
        if (element != null && element.equals(variable)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
      PsiExpression operand = instanceOfExpression.getOperand();
      return expressionAssignsVariableOrFails(operand, variable, checkedMethods, strict);
    }
    else {
      return false;
    }
  }

  private static boolean newExpressionAssignsVariableOrFails(
    @Nonnull PsiNewExpression newExpression, PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods, boolean strict) {
    PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      PsiExpression[] args = argumentList.getExpressions();
      for (PsiExpression arg : args) {
        if (expressionAssignsVariableOrFails(arg, variable,
                                             checkedMethods, strict)) {
          return true;
        }
      }
    }
    PsiArrayInitializerExpression arrayInitializer =
      newExpression.getArrayInitializer();
    if (expressionAssignsVariableOrFails(arrayInitializer, variable,
                                         checkedMethods, strict)) {
      return true;
    }
    PsiExpression[] arrayDimensions =
      newExpression.getArrayDimensions();
    for (PsiExpression dim : arrayDimensions) {
      if (expressionAssignsVariableOrFails(dim, variable,
                                           checkedMethods, strict)) {
        return true;
      }
    }
    return false;
  }

  private static boolean methodCallAssignsVariableOrFails(
    @Nonnull PsiMethodCallExpression callExpression,
    PsiVariable variable,
    @Nonnull Set<MethodSignature> checkedMethods, boolean strict) {
    PsiExpressionList argList = callExpression.getArgumentList();
    PsiExpression[] args = argList.getExpressions();
    for (PsiExpression arg : args) {
      if (expressionAssignsVariableOrFails(arg, variable, checkedMethods,
                                           strict)) {
        return true;
      }
    }
    PsiReferenceExpression methodExpression =
      callExpression.getMethodExpression();
    if (expressionAssignsVariableOrFails(methodExpression, variable,
                                         checkedMethods, strict)) {
      return true;
    }
    PsiMethod method = callExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    MethodSignature methodSignature =
      method.getSignature(PsiSubstitutor.EMPTY);
    if (!checkedMethods.add(methodSignature)) {
      return false;
    }
    PsiClass containingClass =
      ClassUtils.getContainingClass(callExpression);
    PsiClass calledClass = method.getContainingClass();
    if (calledClass == null || !calledClass.equals(containingClass)) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)
      || method.isConstructor()
      || method.hasModifierProperty(PsiModifier.PRIVATE)
      || method.hasModifierProperty(PsiModifier.FINAL)
      || calledClass.hasModifierProperty(PsiModifier.FINAL)) {
      PsiCodeBlock body = method.getBody();
      return blockAssignsVariableOrFails(body, variable,
                                         checkedMethods, strict);
    }
    return false;
  }

  public static boolean isInitializedInConstructors(PsiField field, PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return false;
    }
    for (PsiMethod constructor : constructors) {
      if (!methodAssignsVariableOrFails(constructor, field)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isInitializedInInitializer(@Nonnull PsiField field, @Nonnull PsiClass aClass) {
    PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      PsiCodeBlock body = initializer.getBody();
      if (blockAssignsVariableOrFails(body, field)) {
        return true;
      }
    }
    return false;
  }
}