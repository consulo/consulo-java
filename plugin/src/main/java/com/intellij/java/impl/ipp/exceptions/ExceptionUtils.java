/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.exceptions;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ExceptionUtils {

  private ExceptionUtils() {}

  public static Set<PsiType> getExceptionTypesHandled(PsiTryStatement statement) {
    Set<PsiType> out = new HashSet<PsiType>(10);
    PsiParameter[] parameters = statement.getCatchBlockParameters();
    for (PsiParameter parameter : parameters) {
      PsiType type = parameter.getType();
      if (type instanceof PsiDisjunctionType) {
        PsiDisjunctionType disjunctionType = (PsiDisjunctionType)type;
        List<PsiType> disjunctions = disjunctionType.getDisjunctions();
        out.addAll(disjunctions);
      } else {
        out.add(type);
      }
    }
    return out;
  }

  private static void calculateExceptionsThrownForStatement(PsiStatement statement, Set<PsiType> exceptionTypes) {
    if (statement == null) {
      return;
    }
    if (statement instanceof PsiBreakStatement || statement instanceof PsiContinueStatement) {
      // don't do anything
    }
    else if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        calculateExceptionsThrownForExpression(returnValue, exceptionTypes);
      }
    }
    else if (statement instanceof PsiThrowStatement) {
      PsiThrowStatement throwStatement = (PsiThrowStatement)statement;
      calculateExceptionsThrownForThrowStatement(throwStatement, exceptionTypes);
    }
    else if (statement instanceof PsiExpressionListStatement) {
      PsiExpressionListStatement expressionListStatement = (PsiExpressionListStatement)statement;
      calculateExceptionsThrownForExpressionListStatement(expressionListStatement, exceptionTypes);
    }
    else if (statement instanceof PsiExpressionStatement) {
      PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      PsiExpression expression = expressionStatement.getExpression();
      calculateExceptionsThrownForExpression(expression, exceptionTypes);
    }
    else if (statement instanceof PsiAssertStatement) {
      PsiAssertStatement assertStatement = (PsiAssertStatement)statement;
      calculateExceptionsThrownForAssertStatement(assertStatement, exceptionTypes);
    }
    else if (statement instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      calculateExceptionsThrownForDeclarationStatement(declarationStatement, exceptionTypes);
    }
    else if (statement instanceof PsiForStatement) {
      PsiForStatement forStatement = (PsiForStatement)statement;
      calculateExceptionsThrownForForStatement(forStatement, exceptionTypes);
    }
    else if (statement instanceof PsiForeachStatement) {
      PsiForeachStatement foreachStatement = (PsiForeachStatement)statement;
      calculateExceptionsThrownForForeachStatement(foreachStatement, exceptionTypes);
    }
    else if (statement instanceof PsiWhileStatement) {
      PsiWhileStatement whileStatement = (PsiWhileStatement)statement;
      calculateExceptionsThrownForWhileStatement(whileStatement, exceptionTypes);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)statement;
      calculateExceptionsThrownForDoWhileStatement(doWhileStatement, exceptionTypes);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)statement;
      calculateExceptionsThrownForSynchronizedStatement(synchronizedStatement, exceptionTypes);
    }
    else if (statement instanceof PsiBlockStatement) {
      PsiBlockStatement block = (PsiBlockStatement)statement;
      calculateExceptionsThrownForBlockStatement(block, exceptionTypes);
    }
    else if (statement instanceof PsiLabeledStatement) {
      PsiLabeledStatement labeledStatement = (PsiLabeledStatement)statement;
      calculateExceptionsThrownForLabeledStatement(labeledStatement, exceptionTypes);
    }
    else if (statement instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)statement;
      calculateExceptionsThrownForIfStatement(ifStatement, exceptionTypes);
    }
    else if (statement instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)statement;
      calculateExceptionsThrownForTryStatement(tryStatement, exceptionTypes);
    }
    else if (statement instanceof PsiSwitchStatement) {
      PsiSwitchStatement switchStatement = (PsiSwitchStatement)statement;
      calculateExceptionsThrownForSwitchStatement(switchStatement, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForLabeledStatement(PsiLabeledStatement labeledStatement, Set<PsiType> exceptionTypes) {
    PsiStatement statement = labeledStatement.getStatement();
    calculateExceptionsThrownForStatement(statement, exceptionTypes);
  }

  private static void calculateExceptionsThrownForExpressionListStatement(PsiExpressionListStatement listStatement,
                                                                          Set<PsiType> exceptionTypes) {
    PsiExpressionList expressionList = listStatement.getExpressionList();
    PsiExpression[] expressions = expressionList.getExpressions();
    for (PsiExpression expression : expressions) {
      calculateExceptionsThrownForExpression(expression, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForDeclarationStatement(PsiDeclarationStatement declarationStatement,
                                                                       Set<PsiType> exceptionTypes) {
    PsiElement[] elements = declarationStatement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable)element;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          calculateExceptionsThrownForExpression(initializer, exceptionTypes);
        }
      }
    }
  }

  private static void calculateExceptionsThrownForAssertStatement(PsiAssertStatement assertStatement, Set<PsiType> exceptionTypes) {
    PsiExpression assertCondition = assertStatement.getAssertCondition();
    calculateExceptionsThrownForExpression(assertCondition, exceptionTypes);
    PsiExpression assertDescription = assertStatement.getAssertDescription();
    calculateExceptionsThrownForExpression(assertDescription, exceptionTypes);
  }

  private static void calculateExceptionsThrownForThrowStatement(PsiThrowStatement throwStatement, Set<PsiType> exceptionTypes) {
    PsiExpression exception = throwStatement.getException();
    if (exception == null) {
      return;
    }
    PsiType type = exception.getType();
    if (type != null) {
      exceptionTypes.add(type);
    }
    calculateExceptionsThrownForExpression(exception, exceptionTypes);
  }

  private static void calculateExceptionsThrownForSwitchStatement(PsiSwitchStatement switchStatement, Set<PsiType> exceptionTypes) {
    PsiExpression switchExpression = switchStatement.getExpression();
    calculateExceptionsThrownForExpression(switchExpression, exceptionTypes);
    PsiCodeBlock body = switchStatement.getBody();
    calculateExceptionsThrownForCodeBlock(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForTryStatement(PsiTryStatement tryStatement, Set<PsiType> exceptionTypes) {
    Set<PsiType> exceptionThrown = new HashSet<PsiType>(10);
    PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      calculateExceptionsThrownForResourceList(resourceList, exceptionTypes);
    }
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    calculateExceptionsThrownForCodeBlock(tryBlock, exceptionThrown);
    Set<PsiType> exceptionHandled = getExceptionTypesHandled(tryStatement);
    for (PsiType thrownType : exceptionThrown) {
      boolean found = false;
      for (PsiType handledType : exceptionHandled) {
        if (handledType.isAssignableFrom(thrownType)) {
          found = true;
          break;
        }
      }
      if (!found) {
        exceptionTypes.add(thrownType);
      }
    }
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      calculateExceptionsThrownForCodeBlock(finallyBlock, exceptionTypes);
    }
    PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (PsiCodeBlock catchBlock : catchBlocks) {
      calculateExceptionsThrownForCodeBlock(catchBlock, exceptionTypes);
    }
  }

  public static void calculateExceptionsThrownForResourceList(PsiResourceList resourceList, Set<PsiType> exceptionTypes) {
    List<PsiResourceVariable> resourceVariables = resourceList.getResourceVariables();
    for (PsiResourceVariable variable : resourceVariables) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        calculateExceptionsThrownForExpression(initializer, exceptionTypes);
      }
      PsiType type = variable.getType();
      PsiClassType autoCloseable = getJavaLangAutoCloseable(resourceList);
      if (!(type instanceof PsiClassType) || !autoCloseable.isAssignableFrom(type)) {
        continue;
      }
      PsiClassType classType = (PsiClassType)type;
      PsiClass aClass = classType.resolve();
      if (aClass == null) {
        continue;
      }
      PsiMethod[] closeMethods = aClass.findMethodsByName("close", true);
      for (PsiMethod method : closeMethods) {
        PsiParameterList list = method.getParameterList();
        if (list.getParametersCount() == 0) {
          calculateExceptionsDeclaredForMethod(method, exceptionTypes);
          break;
        }
      }
    }
  }

  private static PsiClassType getJavaLangAutoCloseable(PsiElement context) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    return factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, context.getResolveScope());
  }

  private static void calculateExceptionsThrownForIfStatement(PsiIfStatement ifStatement, Set<PsiType> exceptionTypes) {
    PsiExpression condition = ifStatement.getCondition();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    PsiStatement thenBranch = ifStatement.getThenBranch();
    calculateExceptionsThrownForStatement(thenBranch, exceptionTypes);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    calculateExceptionsThrownForStatement(elseBranch, exceptionTypes);
  }

  private static void calculateExceptionsThrownForBlockStatement(PsiBlockStatement block, Set<PsiType> exceptionTypes) {
    PsiCodeBlock codeBlock = block.getCodeBlock();
    calculateExceptionsThrownForCodeBlock(codeBlock, exceptionTypes);
  }

  private static void calculateExceptionsThrownForSynchronizedStatement(PsiSynchronizedStatement synchronizedStatement,
                                                                        Set<PsiType> exceptionTypes) {
    PsiExpression lockExpression = synchronizedStatement.getLockExpression();
    if (lockExpression != null) {
      calculateExceptionsThrownForExpression(lockExpression, exceptionTypes);
    }
    PsiCodeBlock body = synchronizedStatement.getBody();
    calculateExceptionsThrownForCodeBlock(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForDoWhileStatement(PsiDoWhileStatement doWhileStatement, Set<PsiType> exceptionTypes) {
    PsiExpression condition = doWhileStatement.getCondition();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    PsiStatement body = doWhileStatement.getBody();
    calculateExceptionsThrownForStatement(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForWhileStatement(PsiWhileStatement whileStatement, Set<PsiType> exceptionTypes) {
    PsiExpression condition = whileStatement.getCondition();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    PsiStatement body = whileStatement.getBody();
    calculateExceptionsThrownForStatement(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForForStatement(PsiForStatement forStatement, Set<PsiType> exceptionTypes) {
    PsiStatement initialization = forStatement.getInitialization();
    calculateExceptionsThrownForStatement(initialization, exceptionTypes);
    PsiExpression condition = forStatement.getCondition();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    PsiStatement update = forStatement.getUpdate();
    calculateExceptionsThrownForStatement(update, exceptionTypes);
    PsiStatement body = forStatement.getBody();
    calculateExceptionsThrownForStatement(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForForeachStatement(PsiForeachStatement foreachStatement, Set<PsiType> exceptionTypes) {
    PsiExpression iteratedValue = foreachStatement.getIteratedValue();
    calculateExceptionsThrownForExpression(iteratedValue, exceptionTypes);
    PsiStatement body = foreachStatement.getBody();
    calculateExceptionsThrownForStatement(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForExpression(PsiExpression expression, Set<PsiType> exceptionTypes) {
    if (expression == null) {
      return;
    }
    if (expression instanceof PsiThisExpression || expression instanceof PsiLiteralExpression ||
        expression instanceof PsiSuperExpression || expression instanceof PsiClassObjectAccessExpression) {
    }
    else if (expression instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
      calculateExceptionsThrownForTypeCast(typeCastExpression, exceptionTypes);
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
      calculateExceptionsThrownForInstanceOf(instanceOfExpression, exceptionTypes);
    }
    else if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      PsiExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier != null) {
        calculateExceptionsThrownForExpression(qualifier, exceptionTypes);
      }
    }
    else if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      calculateExceptionsThrownForMethodCall(methodCallExpression, exceptionTypes);
    }
    else if (expression instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)expression;
      calculateExceptionsThrownForNewExpression(newExpression, exceptionTypes);
    }
    else if (expression instanceof PsiArrayInitializerExpression) {
      PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)expression;
      calculateExceptionsThrownForArrayInitializerExpression(arrayInitializerExpression, exceptionTypes);
    }
    else if (expression instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)expression;
      calculateExceptionsThrownForArrayAccessExpression(arrayAccessExpression, exceptionTypes);
    }
    else if (expression instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      calculateExceptionsThrownForPrefixException(prefixExpression, exceptionTypes);
    }
    else if (expression instanceof PsiPostfixExpression) {
      PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      calculateExceptionsThrownForPostfixExpression(postfixExpression, exceptionTypes);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      calculateExceptionsThrownForPolyadicExpression(polyadicExpression, exceptionTypes);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      calculateExceptionsThrownForAssignmentExpression(assignmentExpression, exceptionTypes);
    }
    else if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      calculateExceptionsThrownForConditionalExpression(conditionalExpression, exceptionTypes);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      PsiExpression innerExpression = parenthesizedExpression.getExpression();
      calculateExceptionsThrownForExpression(innerExpression, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForTypeCast(PsiTypeCastExpression typeCastExpression, Set<PsiType> exceptionTypes) {
    PsiExpression operand = typeCastExpression.getOperand();
    calculateExceptionsThrownForExpression(operand, exceptionTypes);
  }

  private static void calculateExceptionsThrownForInstanceOf(PsiInstanceOfExpression instanceOfExpression, Set<PsiType> exceptionTypes) {
    PsiExpression operand = instanceOfExpression.getOperand();
    calculateExceptionsThrownForExpression(operand, exceptionTypes);
  }

  private static void calculateExceptionsThrownForNewExpression(PsiNewExpression newExpression, Set<PsiType> exceptionTypes) {
    PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      PsiExpression[] arguments = argumentList.getExpressions();
      for (PsiExpression argument : arguments) {
        calculateExceptionsThrownForExpression(argument, exceptionTypes);
      }
    }
    PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
    for (PsiExpression arrayDimension : arrayDimensions) {
      calculateExceptionsThrownForExpression(arrayDimension, exceptionTypes);
    }
    PsiExpression qualifier = newExpression.getQualifier();
    calculateExceptionsThrownForExpression(qualifier, exceptionTypes);
    PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
    calculateExceptionsThrownForExpression(arrayInitializer, exceptionTypes);
    PsiMethod method = newExpression.resolveMethod();
    calculateExceptionsDeclaredForMethod(method, exceptionTypes);
  }

  private static void calculateExceptionsThrownForMethodCall(PsiMethodCallExpression methodCallExpression, Set<PsiType> exceptionTypes) {
    PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    PsiExpression[] expressions = argumentList.getExpressions();
    for (PsiExpression expression : expressions) {
      calculateExceptionsThrownForExpression(expression, exceptionTypes);
    }
    PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    calculateExceptionsThrownForExpression(methodExpression, exceptionTypes);
    PsiMethod method = methodCallExpression.resolveMethod();
    calculateExceptionsDeclaredForMethod(method, exceptionTypes);
  }

  public static void calculateExceptionsDeclaredForMethod(PsiMethod method, Set<PsiType> exceptionTypes) {
    if (method == null) {
      return;
    }
    PsiReferenceList throwsList = method.getThrowsList();
    PsiClassType[] types = throwsList.getReferencedTypes();
    Collections.addAll(exceptionTypes, types);
  }

  private static void calculateExceptionsThrownForConditionalExpression(PsiConditionalExpression conditionalExpression,
                                                                        Set<PsiType> exceptionTypes) {
    PsiExpression condition = conditionalExpression.getCondition();
    PsiExpression elseExpression = conditionalExpression.getElseExpression();
    PsiExpression thenExpression = conditionalExpression.getThenExpression();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    calculateExceptionsThrownForExpression(elseExpression, exceptionTypes);
    calculateExceptionsThrownForExpression(thenExpression, exceptionTypes);
  }

  private static void calculateExceptionsThrownForPolyadicExpression(PsiPolyadicExpression polyadicExpression, Set<PsiType> exceptionTypes) {
    PsiExpression[] operands = polyadicExpression.getOperands();
    for (PsiExpression operand : operands) {
      calculateExceptionsThrownForExpression(operand, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForAssignmentExpression(PsiAssignmentExpression assignmentExpression,
                                                                       Set<PsiType> exceptionTypes) {
    PsiExpression lOperand = assignmentExpression.getLExpression();
    calculateExceptionsThrownForExpression(lOperand, exceptionTypes);
    PsiExpression rhs = assignmentExpression.getRExpression();
    calculateExceptionsThrownForExpression(rhs, exceptionTypes);
  }

  private static void calculateExceptionsThrownForArrayInitializerExpression(PsiArrayInitializerExpression arrayInitializerExpression,
                                                                             Set<PsiType> exceptionTypes) {
    PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
    for (PsiExpression initializer : initializers) {
      calculateExceptionsThrownForExpression(initializer, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForArrayAccessExpression(PsiArrayAccessExpression arrayAccessExpression,
                                                                        Set<PsiType> exceptionTypes) {
    PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    calculateExceptionsThrownForExpression(arrayExpression, exceptionTypes);
    PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
    calculateExceptionsThrownForExpression(indexExpression, exceptionTypes);
  }

  private static void calculateExceptionsThrownForPrefixException(PsiPrefixExpression prefixExpression, Set<PsiType> exceptionTypes) {
    PsiExpression operand = prefixExpression.getOperand();
    calculateExceptionsThrownForExpression(operand, exceptionTypes);
  }

  private static void calculateExceptionsThrownForPostfixExpression(PsiPostfixExpression postfixExpression, Set<PsiType> exceptionTypes) {
    PsiExpression operand = postfixExpression.getOperand();
    calculateExceptionsThrownForExpression(operand, exceptionTypes);
  }

  public static void calculateExceptionsThrownForCodeBlock(
    PsiCodeBlock codeBlock, Set<PsiType> exceptionTypes) {
    if (codeBlock == null) {
      return;
    }
    PsiStatement[] statements = codeBlock.getStatements();
    for (PsiStatement statement : statements) {
      calculateExceptionsThrownForStatement(statement, exceptionTypes);
    }
  }
}
