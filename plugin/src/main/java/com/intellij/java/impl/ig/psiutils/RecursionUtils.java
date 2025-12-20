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
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.ast.IElementType;
import com.siyeh.ig.psiutils.ExpressionUtils;

import jakarta.annotation.Nonnull;

public class RecursionUtils {

  private RecursionUtils() {
    super();
  }

  public static boolean statementMayReturnBeforeRecursing(
      PsiStatement statement, PsiMethod method) {
    if (statement == null) {
      return true;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiThrowStatement ||
        statement instanceof PsiExpressionListStatement ||
        statement instanceof PsiExpressionStatement ||
        statement instanceof PsiEmptyStatement ||
        statement instanceof PsiAssertStatement ||
        statement instanceof PsiDeclarationStatement) {
      return false;
    } else if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement =
          (PsiReturnStatement) statement;
      PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        if (expressionDefinitelyRecurses(returnValue, method)) {
          return false;
        }
      }
      return true;
    } else if (statement instanceof PsiForStatement) {
      return forStatementMayReturnBeforeRecursing(
          (PsiForStatement) statement, method);
    } else if (statement instanceof PsiForeachStatement) {
      return foreachStatementMayReturnBeforeRecursing(
          (PsiForeachStatement) statement, method);
    } else if (statement instanceof PsiWhileStatement) {
      return whileStatementMayReturnBeforeRecursing(
          (PsiWhileStatement) statement, method);
    } else if (statement instanceof PsiDoWhileStatement) {
      return doWhileStatementMayReturnBeforeRecursing(
          (PsiDoWhileStatement) statement, method);
    } else if (statement instanceof PsiSynchronizedStatement) {
      PsiCodeBlock body = ((PsiSynchronizedStatement) statement)
          .getBody();
      return codeBlockMayReturnBeforeRecursing(body, method, false);
    } else if (statement instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement =
          (PsiBlockStatement) statement;
      PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      return codeBlockMayReturnBeforeRecursing(codeBlock, method, false);
    } else if (statement instanceof PsiLabeledStatement) {
      return labeledStatementMayReturnBeforeRecursing(
          (PsiLabeledStatement) statement, method);
    } else if (statement instanceof PsiIfStatement) {
      return ifStatementMayReturnBeforeRecursing(
          (PsiIfStatement) statement, method);
    } else if (statement instanceof PsiTryStatement) {
      return tryStatementMayReturnBeforeRecursing(
          (PsiTryStatement) statement, method);
    } else if (statement instanceof PsiSwitchStatement) {
      return switchStatementMayReturnBeforeRecursing(
          (PsiSwitchStatement) statement, method);
    } else {
      // unknown statement type
      return true;
    }
  }

  private static boolean doWhileStatementMayReturnBeforeRecursing(
      PsiDoWhileStatement loopStatement, PsiMethod method) {
    PsiStatement body = loopStatement.getBody();
    return statementMayReturnBeforeRecursing(body, method);
  }

  private static boolean whileStatementMayReturnBeforeRecursing(
      PsiWhileStatement loopStatement, PsiMethod method) {
    PsiExpression test = loopStatement.getCondition();
    if (expressionDefinitelyRecurses(test, method)) {
      return false;
    }
    PsiStatement body = loopStatement.getBody();
    return statementMayReturnBeforeRecursing(body, method);
  }

  private static boolean forStatementMayReturnBeforeRecursing(
      PsiForStatement loopStatement, PsiMethod method) {
    PsiStatement initialization = loopStatement.getInitialization();

    if (statementMayReturnBeforeRecursing(initialization, method)) {
      return true;
    }
    PsiExpression test = loopStatement.getCondition();
    if (expressionDefinitelyRecurses(test, method)) {
      return false;
    }
    PsiStatement body = loopStatement.getBody();
    return statementMayReturnBeforeRecursing(body, method);
  }

  private static boolean foreachStatementMayReturnBeforeRecursing(
      PsiForeachStatement loopStatement, PsiMethod method) {
    PsiExpression test = loopStatement.getIteratedValue();
    if (expressionDefinitelyRecurses(test, method)) {
      return false;
    }
    PsiStatement body = loopStatement.getBody();
    return statementMayReturnBeforeRecursing(body, method);
  }

  private static boolean switchStatementMayReturnBeforeRecursing(
      PsiSwitchStatement switchStatement, PsiMethod method) {

    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return true;
    }
    PsiStatement[] statements = body.getStatements();
    for (PsiStatement statement : statements) {
      if (statementMayReturnBeforeRecursing(statement, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean tryStatementMayReturnBeforeRecursing(
      PsiTryStatement tryStatement, PsiMethod method) {
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      if (codeBlockMayReturnBeforeRecursing(finallyBlock, method,
          false)) {
        return true;
      }
      if (codeBlockDefinitelyRecurses(finallyBlock, method)) {
        return false;
      }
    }
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (codeBlockMayReturnBeforeRecursing(tryBlock, method, false)) {
      return true;
    }
    PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (PsiCodeBlock catchBlock : catchBlocks) {
      if (codeBlockMayReturnBeforeRecursing(catchBlock, method, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementMayReturnBeforeRecursing(
      PsiIfStatement ifStatement, PsiMethod method) {
    PsiExpression test = ifStatement.getCondition();
    if (expressionDefinitelyRecurses(test, method)) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    if (statementMayReturnBeforeRecursing(thenBranch, method)) {
      return true;
    }
    PsiStatement elseBranch = ifStatement.getElseBranch();
    return elseBranch != null &&
        statementMayReturnBeforeRecursing(elseBranch, method);
  }

  private static boolean labeledStatementMayReturnBeforeRecursing(
      PsiLabeledStatement labeledStatement, PsiMethod method) {
    PsiStatement statement = labeledStatement.getStatement();
    return statementMayReturnBeforeRecursing(statement, method);
  }

  private static boolean codeBlockMayReturnBeforeRecursing(
      PsiCodeBlock block, PsiMethod method, boolean endsInImplicitReturn) {
    if (block == null) {
      return true;
    }
    PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      if (statementMayReturnBeforeRecursing(statement, method)) {
        return true;
      }
      if (statementDefinitelyRecurses(statement, method)) {
        return false;
      }
    }
    return endsInImplicitReturn;
  }

  public static boolean methodMayRecurse(@Nonnull PsiMethod method) {
    RecursionVisitor recursionVisitor = new RecursionVisitor(method);
    method.accept(recursionVisitor);
    return recursionVisitor.isRecursive();
  }

  private static boolean expressionDefinitelyRecurses(PsiExpression exp,
                                                      PsiMethod method) {
    if (exp == null) {
      return false;
    }
    if (exp instanceof PsiMethodCallExpression) {
      return methodCallExpressionDefinitelyRecurses(
          (PsiMethodCallExpression) exp, method);
    }
    if (exp instanceof PsiNewExpression) {
      return newExpressionDefinitelyRecurses(
          (PsiNewExpression) exp, method);
    }
    if (exp instanceof PsiAssignmentExpression) {
      return assignmentExpressionDefinitelyRecurses(
          (PsiAssignmentExpression) exp, method);
    }
    if (exp instanceof PsiArrayInitializerExpression) {
      return arrayInitializerExpressionDefinitelyRecurses(
          (PsiArrayInitializerExpression) exp, method);
    }
    if (exp instanceof PsiTypeCastExpression) {
      return typeCastExpressionDefinitelyRecurses(
          (PsiTypeCastExpression) exp, method);
    }
    if (exp instanceof PsiArrayAccessExpression) {
      return arrayAccessExpressionDefinitelyRecurses(
          (PsiArrayAccessExpression) exp, method);
    }
    if (exp instanceof PsiPrefixExpression) {
      return prefixExpressionDefinitelyRecurses(
          (PsiPrefixExpression) exp, method);
    }
    if (exp instanceof PsiPostfixExpression) {
      return postfixExpressionDefinitelyRecurses(
          (PsiPostfixExpression) exp, method);
    }
    if (exp instanceof PsiBinaryExpression) {
      return binaryExpressionDefinitelyRecurses(
          (PsiBinaryExpression) exp, method);
    }
    if (exp instanceof PsiInstanceOfExpression) {
      return instanceOfExpressionDefinitelyRecurses(
          (PsiInstanceOfExpression) exp, method);
    }
    if (exp instanceof PsiConditionalExpression) {
      return conditionalExpressionDefinitelyRecurses(
          (PsiConditionalExpression) exp, method);
    }
    if (exp instanceof PsiParenthesizedExpression) {
      return parenthesizedExpressionDefinitelyRecurses(
          (PsiParenthesizedExpression) exp, method);
    }
    if (exp instanceof PsiReferenceExpression) {
      return referenceExpressionDefinitelyRecurses(
          (PsiReferenceExpression) exp, method);
    }
    if (exp instanceof PsiLiteralExpression ||
        exp instanceof PsiClassObjectAccessExpression ||
        exp instanceof PsiThisExpression ||
        exp instanceof PsiSuperExpression) {
      return false;
    }
    return false;
  }

  private static boolean conditionalExpressionDefinitelyRecurses(
      PsiConditionalExpression expression, PsiMethod method) {
    PsiExpression condExpression = expression.getCondition();
    if (expressionDefinitelyRecurses(condExpression, method)) {
      return true;
    }
    PsiExpression thenExpression = expression.getThenExpression();
    PsiExpression elseExpression = expression.getElseExpression();
    return expressionDefinitelyRecurses(thenExpression, method)
        && expressionDefinitelyRecurses(elseExpression, method);
  }

  private static boolean binaryExpressionDefinitelyRecurses(
      PsiBinaryExpression expression, PsiMethod method) {
    PsiExpression lhs = expression.getLOperand();
    if (expressionDefinitelyRecurses(lhs, method)) {
      return true;
    }
    IElementType tokenType = expression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.ANDAND) ||
        tokenType.equals(JavaTokenType.OROR)) {
      return false;
    }
    PsiExpression rhs = expression.getROperand();
    return expressionDefinitelyRecurses(rhs, method);
  }

  private static boolean arrayAccessExpressionDefinitelyRecurses(
      PsiArrayAccessExpression expression, PsiMethod method) {
    PsiExpression arrayExp = expression.getArrayExpression();
    PsiExpression indexExp = expression.getIndexExpression();
    return expressionDefinitelyRecurses(arrayExp, method) ||
        expressionDefinitelyRecurses(indexExp, method);
  }

  private static boolean arrayInitializerExpressionDefinitelyRecurses(
      PsiArrayInitializerExpression expression, PsiMethod method) {
    PsiExpression[] initializers = expression.getInitializers();
    for (PsiExpression initializer : initializers) {
      if (expressionDefinitelyRecurses(initializer, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean prefixExpressionDefinitelyRecurses(
      PsiPrefixExpression expression, PsiMethod method) {
    PsiExpression operand = expression.getOperand();
    return expressionDefinitelyRecurses(operand, method);
  }

  private static boolean postfixExpressionDefinitelyRecurses(
      PsiPostfixExpression expression, PsiMethod method) {
    PsiExpression operand = expression.getOperand();
    return expressionDefinitelyRecurses(operand, method);
  }

  private static boolean instanceOfExpressionDefinitelyRecurses(
      PsiInstanceOfExpression expression, PsiMethod method) {
    PsiExpression operand = expression.getOperand();
    return expressionDefinitelyRecurses(operand, method);
  }

  private static boolean parenthesizedExpressionDefinitelyRecurses(
      PsiParenthesizedExpression expression, PsiMethod method) {
    PsiExpression innerExpression = expression.getExpression();
    return expressionDefinitelyRecurses(innerExpression, method);
  }

  private static boolean referenceExpressionDefinitelyRecurses(
      PsiReferenceExpression expression, PsiMethod method) {
    PsiExpression qualifierExpression =
        expression.getQualifierExpression();
    return qualifierExpression != null &&
        expressionDefinitelyRecurses(qualifierExpression, method);
  }

  private static boolean typeCastExpressionDefinitelyRecurses(
      PsiTypeCastExpression expression, PsiMethod method) {
    PsiExpression operand = expression.getOperand();
    return expressionDefinitelyRecurses(operand, method);
  }

  private static boolean assignmentExpressionDefinitelyRecurses(
      PsiAssignmentExpression assignmentExpression, PsiMethod method) {
    PsiExpression rhs = assignmentExpression.getRExpression();
    PsiExpression lhs = assignmentExpression.getLExpression();
    return expressionDefinitelyRecurses(rhs, method) ||
        expressionDefinitelyRecurses(lhs, method);
  }

  private static boolean newExpressionDefinitelyRecurses(PsiNewExpression exp,
                                                         PsiMethod method) {
    PsiExpression[] arrayDimensions = exp.getArrayDimensions();
    for (PsiExpression arrayDimension : arrayDimensions) {
      if (expressionDefinitelyRecurses(arrayDimension, method)) {
        return true;
      }
    }
    PsiArrayInitializerExpression arrayInitializer = exp
        .getArrayInitializer();
    if (expressionDefinitelyRecurses(arrayInitializer, method)) {
      return true;
    }
    PsiExpression qualifier = exp.getQualifier();
    if (expressionDefinitelyRecurses(qualifier, method)) {
      return true;
    }
    PsiExpressionList argumentList = exp.getArgumentList();
    if (argumentList != null) {
      PsiExpression[] args = argumentList.getExpressions();
      for (PsiExpression arg : args) {
        if (expressionDefinitelyRecurses(arg, method)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean methodCallExpressionDefinitelyRecurses(
      PsiMethodCallExpression exp, PsiMethod method) {
    PsiReferenceExpression methodExpression =
        exp.getMethodExpression();
    PsiMethod referencedMethod = exp.resolveMethod();
    if (referencedMethod == null) {
      return false;
    }
    if (referencedMethod.equals(method)) {
      if (method.hasModifierProperty(PsiModifier.STATIC) ||
          method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return true;
      }
      PsiExpression qualifier =
          methodExpression.getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        return true;
      }
    }
    PsiExpression qualifier =
        methodExpression.getQualifierExpression();
    if (expressionDefinitelyRecurses(qualifier, method)) {
      return true;
    }
    PsiExpressionList argumentList = exp.getArgumentList();
    PsiExpression[] args = argumentList.getExpressions();
    for (PsiExpression arg : args) {
      if (expressionDefinitelyRecurses(arg, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean statementDefinitelyRecurses(PsiStatement statement,
                                                     PsiMethod method) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiThrowStatement ||
        statement instanceof PsiEmptyStatement ||
        statement instanceof PsiAssertStatement) {
      return false;
    } else if (statement instanceof PsiExpressionListStatement) {
      PsiExpressionListStatement expressionListStatement =
          (PsiExpressionListStatement) statement;
      PsiExpressionList expressionList =
          expressionListStatement.getExpressionList();
      if (expressionList == null) {
        return false;
      }
      PsiExpression[] expressions = expressionList.getExpressions();
      for (PsiExpression expression : expressions) {
        if (expressionDefinitelyRecurses(expression, method)) {
          return true;
        }
      }
      return false;
    } else if (statement instanceof PsiExpressionStatement) {
      PsiExpressionStatement expressionStatement =
          (PsiExpressionStatement) statement;
      PsiExpression expression =
          expressionStatement.getExpression();
      return expressionDefinitelyRecurses(expression, method);
    } else if (statement instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement declaration =
          (PsiDeclarationStatement) statement;
      PsiElement[] declaredElements =
          declaration.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiLocalVariable) {
          PsiLocalVariable variable =
              (PsiLocalVariable) declaredElement;
          PsiExpression initializer = variable.getInitializer();
          if (expressionDefinitelyRecurses(initializer, method)) {
            return true;
          }
        }
      }
      return false;
    } else if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement =
          (PsiReturnStatement) statement;
      PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        if (expressionDefinitelyRecurses(returnValue, method)) {
          return true;
        }
      }
      return false;
    } else if (statement instanceof PsiForStatement) {
      return forStatementDefinitelyRecurses((PsiForStatement)
          statement, method);
    } else if (statement instanceof PsiForeachStatement) {
      return foreachStatementDefinitelyRecurses(
          (PsiForeachStatement) statement, method);
    } else if (statement instanceof PsiWhileStatement) {
      return whileStatementDefinitelyRecurses(
          (PsiWhileStatement) statement, method);
    } else if (statement instanceof PsiDoWhileStatement) {
      return doWhileStatementDefinitelyRecurses(
          (PsiDoWhileStatement) statement, method);
    } else if (statement instanceof PsiSynchronizedStatement) {
      PsiCodeBlock body = ((PsiSynchronizedStatement) statement)
          .getBody();
      return codeBlockDefinitelyRecurses(body, method);
    } else if (statement instanceof PsiBlockStatement) {
      PsiCodeBlock codeBlock = ((PsiBlockStatement) statement)
          .getCodeBlock();
      return codeBlockDefinitelyRecurses(codeBlock, method);
    } else if (statement instanceof PsiLabeledStatement) {
      return labeledStatementDefinitelyRecurses(
          (PsiLabeledStatement) statement, method);
    } else if (statement instanceof PsiIfStatement) {
      return ifStatementDefinitelyRecurses(
          (PsiIfStatement) statement, method);
    } else if (statement instanceof PsiTryStatement) {
      return tryStatementDefinitelyRecurses(
          (PsiTryStatement) statement, method);
    } else if (statement instanceof PsiSwitchStatement) {
      return switchStatementDefinitelyRecurses(
          (PsiSwitchStatement) statement, method);
    } else {
      // unknown statement type
      return false;
    }
  }

  private static boolean switchStatementDefinitelyRecurses(
      PsiSwitchStatement switchStatement, PsiMethod method) {
    PsiExpression switchExpression = switchStatement.getExpression();
    return expressionDefinitelyRecurses(switchExpression, method);
  }

  private static boolean tryStatementDefinitelyRecurses(
      PsiTryStatement tryStatement, PsiMethod method) {
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (codeBlockDefinitelyRecurses(tryBlock, method)) {
      return true;
    }
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    return codeBlockDefinitelyRecurses(finallyBlock, method);
  }

  private static boolean codeBlockDefinitelyRecurses(PsiCodeBlock block,
                                                     PsiMethod method) {
    if (block == null) {
      return false;
    }
    PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      if (statementDefinitelyRecurses(statement, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementDefinitelyRecurses(
      PsiIfStatement ifStatement, PsiMethod method) {
    PsiExpression condition = ifStatement.getCondition();
    if (expressionDefinitelyRecurses(condition, method)) {
      return true;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (thenBranch == null) {
      return false;
    }
    Object value =
        ExpressionUtils.computeConstantExpression(condition);
    if (value == Boolean.TRUE) {
      return statementDefinitelyRecurses(thenBranch, method);
    } else if (value == Boolean.FALSE) {
      return elseBranch != null &&
          statementDefinitelyRecurses(elseBranch, method);
    }
    return statementDefinitelyRecurses(thenBranch, method) &&
        statementDefinitelyRecurses(elseBranch, method);
  }

  private static boolean forStatementDefinitelyRecurses(
      PsiForStatement forStatement, PsiMethod method) {
    PsiStatement initialization = forStatement.getInitialization();
    if (statementDefinitelyRecurses(initialization, method)) {
      return true;
    }
    PsiExpression condition = forStatement.getCondition();
    if (expressionDefinitelyRecurses(condition, method)) {
      return true;
    }
    Object value =
        ExpressionUtils.computeConstantExpression(condition);
    if (value == Boolean.TRUE) {
      PsiStatement body = forStatement.getBody();
      return statementDefinitelyRecurses(body, method);
    }
    return false;
  }

  private static boolean foreachStatementDefinitelyRecurses(
      PsiForeachStatement foreachStatement, PsiMethod method) {
    PsiExpression iteration = foreachStatement.getIteratedValue();
    return expressionDefinitelyRecurses(iteration, method);
  }

  private static boolean whileStatementDefinitelyRecurses(
      PsiWhileStatement whileStatement, PsiMethod method) {

    PsiExpression condition = whileStatement.getCondition();
    if (expressionDefinitelyRecurses(condition, method)) {
      return true;
    }
    Object value =
        ExpressionUtils.computeConstantExpression(condition);
    if (value == Boolean.TRUE) {
      PsiStatement body = whileStatement.getBody();
      return statementDefinitelyRecurses(body, method);
    }
    return false;
  }

  private static boolean doWhileStatementDefinitelyRecurses(
      PsiDoWhileStatement doWhileStatement, PsiMethod method) {

    PsiStatement body = doWhileStatement.getBody();
    if (statementDefinitelyRecurses(body, method)) {
      return true;
    }
    PsiExpression condition = doWhileStatement.getCondition();
    return expressionDefinitelyRecurses(condition, method);
  }

  private static boolean labeledStatementDefinitelyRecurses(
      PsiLabeledStatement labeledStatement, PsiMethod method) {
    PsiStatement body = labeledStatement.getStatement();
    return statementDefinitelyRecurses(body, method);
  }

  public static boolean methodDefinitelyRecurses(
      @Nonnull PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    return body != null &&
        !codeBlockMayReturnBeforeRecursing(body, method, true);
  }
}
