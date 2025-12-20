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
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ClassUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UninitializedReadCollector {

  private final Set<PsiExpression> uninitializedReads;
  private int counter = 0;

  public UninitializedReadCollector() {
    uninitializedReads = new HashSet<PsiExpression>();
  }

  public PsiExpression[] getUninitializedReads() {
    return uninitializedReads.toArray(new PsiExpression[uninitializedReads.size()]);
  }

  public boolean blockAssignsVariable(@Nullable PsiCodeBlock block, @Nonnull PsiVariable variable) {
    return blockAssignsVariable(block, variable, counter, new HashSet<MethodSignature>());
  }

  private boolean blockAssignsVariable(@Nullable PsiCodeBlock block, @Nonnull PsiVariable variable,
                                       int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    if (counter != stamp) {
      return true;
    }
    if (block == null) {
      return false;
    }
    PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      if (statementAssignsVariable(statement, variable, stamp, checkedMethods)) {
        return true;
      }
      if (counter != stamp) {
        return true;
      }
    }
    return false;
  }

  private boolean statementAssignsVariable(@Nullable PsiStatement statement, @Nonnull PsiVariable variable,
                                           int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    if (statement == null) {
      return false;
    }
    if (ExceptionUtils.statementThrowsException(statement)) {
      return true;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiAssertStatement ||
        statement instanceof PsiEmptyStatement) {
      return false;
    } else if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
      PsiExpression returnValue = returnStatement.getReturnValue();
      return expressionAssignsVariable(returnValue, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiThrowStatement) {
      PsiThrowStatement throwStatement = (PsiThrowStatement) statement;
      PsiExpression exception = throwStatement.getException();
      return expressionAssignsVariable(exception, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiExpressionListStatement) {
      PsiExpressionListStatement list = (PsiExpressionListStatement) statement;
      PsiExpressionList expressionList = list.getExpressionList();
      PsiExpression[] expressions = expressionList.getExpressions();
      for (PsiExpression expression : expressions) {
        if (expressionAssignsVariable(expression, variable, stamp, checkedMethods)) {
          return true;
        }
      }
      return false;
    } else if (statement instanceof PsiExpressionStatement) {
      PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
      PsiExpression expression = expressionStatement.getExpression();
      return expressionAssignsVariable(expression, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) statement;
      return declarationStatementAssignsVariable(declarationStatement, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiForStatement) {
      PsiForStatement forStatement = (PsiForStatement) statement;
      return forStatementAssignsVariable(forStatement, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiForeachStatement) {
      PsiForeachStatement foreachStatement = (PsiForeachStatement) statement;
      return foreachStatementAssignsVariable(foreachStatement, variable);
    } else if (statement instanceof PsiWhileStatement) {
      PsiWhileStatement whileStatement = (PsiWhileStatement) statement;
      return whileStatementAssignsVariable(whileStatement, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiDoWhileStatement) {
      PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement) statement;
      return doWhileAssignsVariable(doWhileStatement, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiSynchronizedStatement) {
      PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement) statement;
      PsiCodeBlock body = synchronizedStatement.getBody();
      return blockAssignsVariable(body, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement = (PsiBlockStatement) statement;
      PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      return blockAssignsVariable(codeBlock, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiLabeledStatement) {
      PsiLabeledStatement labeledStatement = (PsiLabeledStatement) statement;
      PsiStatement statementLabeled = labeledStatement.getStatement();
      return statementAssignsVariable(statementLabeled, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement) statement;
      return ifStatementAssignsVariable(ifStatement, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement) statement;
      return tryStatementAssignsVariable(tryStatement, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiSwitchStatement) {
      PsiSwitchStatement switchStatement = (PsiSwitchStatement) statement;
      return switchStatementAssignsVariable(switchStatement, variable, stamp, checkedMethods);
    } else if (statement instanceof PsiSwitchLabelStatement) {
      return false;
    } else {
      assert false : "unknown statement: " + statement;
      return false;
    }
  }

  private boolean switchStatementAssignsVariable(@Nonnull PsiSwitchStatement switchStatement, @Nonnull PsiVariable variable,
                                                 int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiExpression expression = switchStatement.getExpression();
    if (expressionAssignsVariable(expression, variable, stamp, checkedMethods)) {
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
        PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement) statement;
        if (i == statements.length - 1) {
          return false;
        }
        if (labelStatement.isDefaultCase()) {
          containsDefault = true;
        }
        assigns = false;
      } else if (statement instanceof PsiBreakStatement) {
        PsiBreakStatement breakStatement = (PsiBreakStatement) statement;
        if (breakStatement.getLabelIdentifier() != null) {
          return false;
        }
        if (!assigns) {
          return false;
        }
        assigns = false;
      } else {
        assigns |= statementAssignsVariable(statement, variable, stamp, checkedMethods);
        if (i == statements.length - 1 && !assigns) {
          return false;
        }
      }
    }
    return containsDefault;
  }

  private boolean declarationStatementAssignsVariable(@Nonnull PsiDeclarationStatement declarationStatement, @Nonnull PsiVariable variable,
                                                      int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiElement[] elements = declarationStatement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiVariable) {
        PsiVariable variableElement = (PsiVariable) element;
        PsiExpression initializer = variableElement.getInitializer();
        if (expressionAssignsVariable(initializer, variable, stamp, checkedMethods)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean tryStatementAssignsVariable(@Nonnull PsiTryStatement tryStatement, @Nonnull PsiVariable variable,
                                              int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      List<PsiResourceVariable> resourceVariables = resourceList.getResourceVariables();
      for (PsiResourceVariable resourceVariable : resourceVariables) {
        PsiExpression initializer = resourceVariable.getInitializer();
        if (expressionAssignsVariable(initializer, variable, stamp, checkedMethods)) {
          return true;
        }
      }
    }
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    boolean initializedInTryOrCatch = blockAssignsVariable(tryBlock, variable, stamp, checkedMethods);
    PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (PsiCodeBlock catchBlock : catchBlocks) {
      initializedInTryOrCatch &= blockAssignsVariable(catchBlock, variable, stamp, checkedMethods);
    }
    if (initializedInTryOrCatch) {
      return true;
    }
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    return blockAssignsVariable(finallyBlock, variable, stamp, checkedMethods);
  }

  private boolean ifStatementAssignsVariable(@Nonnull PsiIfStatement ifStatement, @Nonnull PsiVariable variable,
                                             int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiExpression condition = ifStatement.getCondition();
    if (expressionAssignsVariable(condition, variable, stamp, checkedMethods)) {
      return true;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();
    return statementAssignsVariable(thenBranch, variable, stamp, checkedMethods) &&
        statementAssignsVariable(elseBranch, variable, stamp, checkedMethods);
  }

  private boolean doWhileAssignsVariable(@Nonnull PsiDoWhileStatement doWhileStatement, @Nonnull PsiVariable variable,
                                         int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiExpression condition = doWhileStatement.getCondition();
    PsiStatement body = doWhileStatement.getBody();
    return statementAssignsVariable(body, variable, stamp, checkedMethods) ||
        expressionAssignsVariable(condition, variable, stamp, checkedMethods);
  }

  private boolean whileStatementAssignsVariable(@Nonnull PsiWhileStatement whileStatement, @Nonnull PsiVariable variable,
                                                int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiExpression condition = whileStatement.getCondition();
    if (expressionAssignsVariable(condition, variable, stamp, checkedMethods)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      PsiStatement body = whileStatement.getBody();
      if (statementAssignsVariable(body, variable, stamp, checkedMethods)) {
        return true;
      }
    }
    return false;
  }

  private boolean forStatementAssignsVariable(@Nonnull PsiForStatement forStatement, @Nonnull PsiVariable variable,
                                              int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiStatement initialization = forStatement.getInitialization();
    if (statementAssignsVariable(initialization, variable, stamp, checkedMethods)) {
      return true;
    }
    PsiExpression condition = forStatement.getCondition();
    if (expressionAssignsVariable(condition, variable, stamp, checkedMethods)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      PsiStatement body = forStatement.getBody();
      if (statementAssignsVariable(body, variable, stamp, checkedMethods)) {
        return true;
      }
      PsiStatement update = forStatement.getUpdate();
      if (statementAssignsVariable(update, variable, stamp, checkedMethods)) {
        return true;
      }
    }
    return false;
  }

  private boolean foreachStatementAssignsVariable(
      PsiForeachStatement forStatement, PsiVariable variable) {
    return false;
  }

  private boolean expressionAssignsVariable(@Nullable PsiExpression expression, @Nonnull PsiVariable variable,
                                            int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    if (counter != stamp) {
      return true;
    }
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiThisExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiSuperExpression ||
        expression instanceof PsiClassObjectAccessExpression) {
      return false;
    } else if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
      return referenceExpressionAssignsVariable(referenceExpression, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression callExpression = (PsiMethodCallExpression) expression;
      return methodCallAssignsVariable(callExpression, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression) expression;
      return newExpressionAssignsVariable(newExpression, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiArrayInitializerExpression) {
      PsiArrayInitializerExpression array = (PsiArrayInitializerExpression) expression;
      PsiExpression[] initializers = array.getInitializers();
      for (PsiExpression initializer : initializers) {
        if (expressionAssignsVariable(initializer, variable, stamp, checkedMethods)) {
          return true;
        }
      }
      return false;
    } else if (expression instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression typeCast = (PsiTypeCastExpression) expression;
      PsiExpression operand = typeCast.getOperand();
      return expressionAssignsVariable(operand, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression accessExpression = (PsiArrayAccessExpression) expression;
      PsiExpression arrayExpression = accessExpression.getArrayExpression();
      PsiExpression indexExpression = accessExpression.getIndexExpression();
      return expressionAssignsVariable(arrayExpression, variable, stamp, checkedMethods) ||
          expressionAssignsVariable(indexExpression, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
      PsiExpression operand = prefixExpression.getOperand();
      return expressionAssignsVariable(operand, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiPostfixExpression) {
      PsiPostfixExpression postfixExpression = (PsiPostfixExpression) expression;
      PsiExpression operand = postfixExpression.getOperand();
      return expressionAssignsVariable(operand, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
      PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (expressionAssignsVariable(operand, variable, stamp, checkedMethods)) {
          return true;
        }
      }
      return false;
    } else if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression) expression;
      PsiExpression condition = conditional.getCondition();
      if (expressionAssignsVariable(condition, variable, stamp, checkedMethods)) {
        return true;
      }
      PsiExpression thenExpression = conditional.getThenExpression();
      PsiExpression elseExpression = conditional.getElseExpression();
      return expressionAssignsVariable(thenExpression, variable, stamp, checkedMethods)
          && expressionAssignsVariable(elseExpression, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression) expression;
      return assignmentExpressionAssignsVariable(assignment, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
      PsiExpression innerExpression = parenthesizedExpression.getExpression();
      return expressionAssignsVariable(innerExpression, variable, stamp, checkedMethods);
    } else if (expression instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression) expression;
      PsiExpression operand = instanceOfExpression.getOperand();
      return expressionAssignsVariable(operand, variable, stamp, checkedMethods);
    } else {
      return false;
    }
  }

  private boolean assignmentExpressionAssignsVariable(@Nonnull PsiAssignmentExpression assignment, @Nonnull PsiVariable variable,
                                                      int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiExpression lhs = assignment.getLExpression();
    if (expressionAssignsVariable(lhs, variable, stamp, checkedMethods)) {
      return true;
    }
    PsiExpression rhs = assignment.getRExpression();
    if (expressionAssignsVariable(rhs, variable, stamp, checkedMethods)) {
      return true;
    }
    if (lhs instanceof PsiReferenceExpression) {
      PsiElement element = ((PsiReference) lhs).resolve();
      if (element != null && element.equals(variable)) {
        return true;
      }
    }
    return false;
  }

  private boolean referenceExpressionAssignsVariable(@Nonnull PsiReferenceExpression referenceExpression, @Nonnull PsiVariable variable,
                                                     int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
    if (expressionAssignsVariable(qualifierExpression, variable, stamp, checkedMethods)) {
      return true;
    }
    if (variable.equals(referenceExpression.resolve())) {
      PsiElement parent = referenceExpression.getParent();
      if (parent instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
        PsiExpression rhs = assignmentExpression.getRExpression();
        if (rhs != null && rhs.equals(referenceExpression)) {
          checkReferenceExpression(referenceExpression, variable, qualifierExpression);
        }
      } else {
        checkReferenceExpression(referenceExpression, variable, qualifierExpression);
      }
    }
    return false;
  }

  private void checkReferenceExpression(PsiReferenceExpression referenceExpression, PsiVariable variable,
                                        PsiExpression qualifierExpression) {
    if (!referenceExpression.isQualified() || qualifierExpression instanceof PsiThisExpression) {
      uninitializedReads.add(referenceExpression);
      counter++;
    } else if (variable.hasModifierProperty(PsiModifier.STATIC) && qualifierExpression instanceof PsiReferenceExpression) {
      PsiReferenceExpression reference = (PsiReferenceExpression) qualifierExpression;
      PsiElement target = reference.resolve();
      if (target instanceof PsiClass) {
        if (target.equals(PsiTreeUtil.getParentOfType(variable, PsiClass.class))) {
          uninitializedReads.add(referenceExpression);
          counter++;
        }
      }
    }
  }

  private boolean newExpressionAssignsVariable(@Nonnull PsiNewExpression newExpression, @Nonnull PsiVariable variable,
                                               int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      PsiExpression[] args = argumentList.getExpressions();
      for (PsiExpression arg : args) {
        if (expressionAssignsVariable(arg, variable, stamp, checkedMethods)) {
          return true;
        }
      }
    }
    PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
    if (expressionAssignsVariable(arrayInitializer, variable, stamp, checkedMethods)) {
      return true;
    }
    PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
    for (PsiExpression dim : arrayDimensions) {
      if (expressionAssignsVariable(dim, variable, stamp, checkedMethods)) {
        return true;
      }
    }
    return false;
  }

  private boolean methodCallAssignsVariable(@Nonnull PsiMethodCallExpression callExpression, @Nonnull PsiVariable variable,
                                            int stamp, @Nonnull Set<MethodSignature> checkedMethods) {
    PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    if (expressionAssignsVariable(methodExpression, variable, stamp, checkedMethods)) {
      return true;
    }
    PsiExpressionList argumentList = callExpression.getArgumentList();
    PsiExpression[] arguments = argumentList.getExpressions();
    for (PsiExpression argument : arguments) {
      if (expressionAssignsVariable(argument, variable, stamp, checkedMethods)) {
        return true;
      }
    }
    PsiMethod method = callExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    if (!checkedMethods.add(methodSignature)) {
      return false;
    }
    PsiClass containingClass = ClassUtils.getContainingClass(callExpression);
    PsiClass calledClass = method.getContainingClass();

    // Can remark out this block to continue chase outside of of
    // current class
    if (calledClass == null || !calledClass.equals(containingClass)) {
      return false;
    }

    if (method.hasModifierProperty(PsiModifier.STATIC)
        || method.isConstructor()
        || method.hasModifierProperty(PsiModifier.PRIVATE)
        || method.hasModifierProperty(PsiModifier.FINAL)
        || calledClass.hasModifierProperty(PsiModifier.FINAL)) {
      PsiCodeBlock body = method.getBody();
      return blockAssignsVariable(body, variable, stamp, checkedMethods);
    }
    return false;
  }
}