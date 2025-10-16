/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

public class VariableAccessUtils {

  private VariableAccessUtils() {
  }

  public static boolean variableIsAssignedFrom(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableAssignedFromVisitor visitor = new VariableAssignedFromVisitor(variable);
    context.accept(visitor);
    return visitor.isAssignedFrom();
  }

  public static boolean variableIsPassedAsMethodArgument(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariablePassedAsArgumentVisitor visitor = new VariablePassedAsArgumentVisitor(variable);
    context.accept(visitor);
    return visitor.isPassed();
  }

  public static boolean variableIsPassedAsMethodArgument(@Nonnull PsiVariable variable, Set<String> excludes, @Nullable PsiElement context) {
    return variableIsPassedAsMethodArgument(variable, excludes, context, false);
  }

  public static boolean variableIsPassedAsMethodArgument(@Nonnull PsiVariable variable, Set<String> excludes, @Nullable PsiElement context,
                                                         boolean builderPattern) {
    if (context == null) {
      return false;
    }
    final VariablePassedAsArgumentExcludedVisitor visitor = new VariablePassedAsArgumentExcludedVisitor(variable, excludes, builderPattern);
    context.accept(visitor);
    return visitor.isPassed();
  }

  public static boolean variableIsUsedInArrayInitializer(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableUsedInArrayInitializerVisitor visitor = new VariableUsedInArrayInitializerVisitor(variable);
    context.accept(visitor);
    return visitor.isPassed();
  }

  public static boolean variableIsAssigned(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variable, true);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  /**
   * This method will return true if the specified variable is a field with greater than private visibility with a common name.
   * Finding usages for such fields is too expensive.
   *
   * @param variable the variable to check assignments for
   * @return true, if the variable is assigned or too expensive to search. False otherwise.
   */
  public static boolean variableIsAssigned(@Nonnull PsiVariable variable) {
    if (variable instanceof PsiField) {
      if (variable.hasModifierProperty(PsiModifier.PRIVATE)) {
        final PsiClass aClass = PsiUtil.getTopLevelClass(variable);
        return variableIsAssigned(variable, aClass);
      }
      return DeclarationSearchUtils.isTooExpensiveToSearch(variable, false) || ReferencesSearch.search(variable).anyMatch(reference -> {
        final PsiExpression expression = ObjectUtil.tryCast(reference.getElement(), PsiExpression.class);
        return expression != null && PsiUtil.isAccessedForWriting(expression);
      });
    }
    final PsiElement context =
        PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiMethod.class, PsiLambdaExpression.class,
            PsiCatchSection.class, PsiForStatement.class, PsiForeachStatement.class);
    return variableIsAssigned(variable, context);
  }

  public static boolean variableIsAssigned(@Nonnull PsiVariable variable, @Nullable PsiElement context, boolean recurseIntoClasses) {
    if (context == null) {
      return false;
    }
    final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variable, recurseIntoClasses);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  public static boolean variableIsReturned(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    return variableIsReturned(variable, context, false);
  }

  public static boolean variableIsReturned(@Nonnull PsiVariable variable, @Nullable PsiElement context, boolean builderPattern) {
    if (context == null) {
      return false;
    }
    final VariableReturnedVisitor visitor = new VariableReturnedVisitor(variable, builderPattern);
    context.accept(visitor);
    return visitor.isReturned();
  }

  public static boolean variableValueIsUsed(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableValueUsedVisitor visitor = new VariableValueUsedVisitor(variable);
    context.accept(visitor);
    return visitor.isVariableValueUsed();
  }

  public static boolean arrayContentsAreAccessed(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final ArrayContentsAccessedVisitor visitor = new ArrayContentsAccessedVisitor(variable);
    context.accept(visitor);
    return visitor.isAccessed();
  }

  public static boolean arrayContentsAreAssigned(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final ArrayContentsAssignedVisitor visitor = new ArrayContentsAssignedVisitor(variable);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  public static boolean variableIsUsedInInnerClass(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableUsedInInnerClassVisitor visitor = new VariableUsedInInnerClassVisitor(variable);
    context.accept(visitor);
    return visitor.isUsedInInnerClass();
  }

  public static boolean mayEvaluateToVariable(@Nullable PsiExpression expression, @Nonnull PsiVariable variable) {
    return mayEvaluateToVariable(expression, variable, false);
  }

  public static List<PsiReferenceExpression> getVariableReferences(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null)
      return Collections.emptyList();
    List<PsiReferenceExpression> result = new ArrayList<>();
    PsiTreeUtil.processElements(context, e -> {
      if (e instanceof PsiReferenceExpression && ((PsiReferenceExpression) e).isReferenceTo(variable)) {
        result.add((PsiReferenceExpression) e);
      }
      return true;
    });
    return result;
  }

  public static boolean mayEvaluateToVariable(@Nullable PsiExpression expression, @Nonnull PsiVariable variable, boolean builderPattern) {
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
      final PsiExpression containedExpression = parenthesizedExpression.getExpression();
      return mayEvaluateToVariable(containedExpression, variable, builderPattern);
    }
    if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression) expression;
      final PsiExpression containedExpression = typeCastExpression.getOperand();
      return mayEvaluateToVariable(containedExpression, variable, builderPattern);
    }
    if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditional = (PsiConditionalExpression) expression;
      final PsiExpression thenExpression = conditional.getThenExpression();
      final PsiExpression elseExpression = conditional.getElseExpression();
      return mayEvaluateToVariable(thenExpression, variable, builderPattern) || mayEvaluateToVariable(elseExpression, variable,
          builderPattern);
    }
    if (expression instanceof PsiArrayAccessExpression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiArrayAccessExpression) {
        return false;
      }
      final PsiType type = variable.getType();
      if (!(type instanceof PsiArrayType)) {
        return false;
      }
      final PsiArrayType arrayType = (PsiArrayType) type;
      final int dimensions = arrayType.getArrayDimensions();
      if (dimensions <= 1) {
        return false;
      }
      PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression) expression;
      PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
      int count = 1;
      while (arrayExpression instanceof PsiArrayAccessExpression) {
        arrayAccessExpression = (PsiArrayAccessExpression) arrayExpression;
        arrayExpression = arrayAccessExpression.getArrayExpression();
        count++;
      }
      return count != dimensions && mayEvaluateToVariable(arrayExpression, variable, builderPattern);
    }
    if (builderPattern && expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiType returnType = method.getReturnType();
      final PsiType variableType = variable.getType();
      if (!variableType.equals(returnType)) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      return mayEvaluateToVariable(qualifier, variable, builderPattern);
    }
    return evaluatesToVariable(expression, variable);
  }

  public static boolean evaluatesToVariable(@Nullable PsiExpression expression, @Nonnull PsiVariable variable) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
    final PsiElement target = referenceExpression.resolve();
    return variable.equals(target);
  }

  public static boolean variableIsUsed(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableUsedVisitor visitor = new VariableUsedVisitor(variable);
    context.accept(visitor);
    return visitor.isUsed();
  }

  public static boolean variableIsDecremented(@Nonnull PsiVariable variable, @Nullable PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
    PsiExpression expression = expressionStatement.getExpression();
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return false;
      }
      final PsiExpression operand = prefixExpression.getOperand();
      return evaluatesToVariable(operand, variable);
    } else if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression) expression;
      final IElementType tokenType = postfixExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return false;
      }
      final PsiExpression operand = postfixExpression.getOperand();
      return evaluatesToVariable(operand, variable);
    } else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expression;
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!evaluatesToVariable(lhs, variable)) {
        return false;
      }
      PsiExpression rhs = assignmentExpression.getRExpression();
      rhs = ParenthesesUtils.stripParentheses(rhs);
      if (tokenType == JavaTokenType.EQ) {
        if (!(rhs instanceof PsiBinaryExpression)) {
          return false;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) rhs;
        final IElementType binaryTokenType = binaryExpression.getOperationTokenType();
        if (binaryTokenType != JavaTokenType.MINUS) {
          return false;
        }
        final PsiExpression lOperand = binaryExpression.getLOperand();
        final PsiExpression rOperand = binaryExpression.getROperand();
        if (ExpressionUtils.isOne(lOperand)) {
          if (evaluatesToVariable(rOperand, variable)) {
            return true;
          }
        } else if (ExpressionUtils.isOne(rOperand)) {
          if (evaluatesToVariable(lOperand, variable)) {
            return true;
          }
        }
      } else if (tokenType == JavaTokenType.MINUSEQ) {
        if (ExpressionUtils.isOne(rhs)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean variableIsIncremented(@Nonnull PsiVariable variable, @Nullable PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
    PsiExpression expression = expressionStatement.getExpression();
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
        return false;
      }
      final PsiExpression operand = prefixExpression.getOperand();
      return evaluatesToVariable(operand, variable);
    } else if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression) expression;
      final IElementType tokenType = postfixExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
        return false;
      }
      final PsiExpression operand = postfixExpression.getOperand();
      return evaluatesToVariable(operand, variable);
    } else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expression;
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!evaluatesToVariable(lhs, variable)) {
        return false;
      }
      PsiExpression rhs = assignmentExpression.getRExpression();
      rhs = ParenthesesUtils.stripParentheses(rhs);
      if (tokenType == JavaTokenType.EQ) {
        if (!(rhs instanceof PsiBinaryExpression)) {
          return false;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) rhs;
        final IElementType binaryTokenType = binaryExpression.getOperationTokenType();
        if (binaryTokenType != JavaTokenType.PLUS) {
          return false;
        }
        final PsiExpression lOperand = binaryExpression.getLOperand();
        final PsiExpression rOperand = binaryExpression.getROperand();
        if (ExpressionUtils.isOne(lOperand)) {
          if (evaluatesToVariable(rOperand, variable)) {
            return true;
          }
        } else if (ExpressionUtils.isOne(rOperand)) {
          if (evaluatesToVariable(lOperand, variable)) {
            return true;
          }
        }
      } else if (tokenType == JavaTokenType.PLUSEQ) {
        if (ExpressionUtils.isOne(rhs)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean variableIsAssignedBeforeReference(@Nonnull PsiReferenceExpression referenceExpression, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable variable = (PsiVariable) target;
    return variableIsAssignedAtPoint(variable, context, referenceExpression);
  }

  public static boolean variableIsAssignedAtPoint(@Nonnull PsiVariable variable, @Nullable PsiElement context, @Nonnull PsiElement point) {
    if (context == null) {
      return false;
    }
    final PsiElement directChild = getDirectChildWhichContainsElement(context, point);
    if (directChild == null) {
      return false;
    }
    final PsiElement[] children = context.getChildren();
    for (PsiElement child : children) {
      if (child == directChild) {
        return variableIsAssignedAtPoint(variable, directChild, point);
      }
      if (variableIsAssigned(variable, child)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiElement getDirectChildWhichContainsElement(@Nonnull PsiElement ancestor, @Nonnull PsiElement descendant) {
    if (ancestor == descendant) {
      return null;
    }
    PsiElement child = descendant;
    PsiElement parent = child.getParent();
    while (!parent.equals(ancestor)) {
      child = parent;
      parent = child.getParent();
      if (parent == null) {
        return null;
      }
    }
    return child;
  }

  public static Set<PsiVariable> collectUsedVariables(PsiElement context) {
    if (context == null) {
      return Collections.emptySet();
    }
    final VariableCollectingVisitor visitor = new VariableCollectingVisitor();
    context.accept(visitor);
    return visitor.getUsedVariables();
  }

  public static boolean isAnyVariableAssigned(@Nonnull Collection<PsiVariable> variables, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variables, true);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  private static class VariableCollectingVisitor extends JavaRecursiveElementVisitor {

    private final Set<PsiVariable> usedVariables = new HashSet();

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiVariable)) {
        return;
      }
      final PsiVariable variable = (PsiVariable) target;
      usedVariables.add(variable);
    }

    public Set<PsiVariable> getUsedVariables() {
      return usedVariables;
    }
  }
}