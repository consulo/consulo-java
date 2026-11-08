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

import org.jspecify.annotations.Nullable;
import java.util.*;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiCallExpression;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiInstanceOfExpression;
import com.intellij.java.language.psi.PsiLambdaParameterType;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.PsiFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VariableAccessUtils {

  private VariableAccessUtils() {
  }

  public static boolean variableIsAssignedFrom(PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableAssignedFromVisitor visitor = new VariableAssignedFromVisitor(variable);
    context.accept(visitor);
    return visitor.isAssignedFrom();
  }

  public static boolean variableIsPassedAsMethodArgument(PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariablePassedAsArgumentVisitor visitor = new VariablePassedAsArgumentVisitor(variable);
    context.accept(visitor);
    return visitor.isPassed();
  }

  public static boolean variableIsPassedAsMethodArgument(PsiVariable variable, Set<String> excludes, @Nullable PsiElement context) {
    return variableIsPassedAsMethodArgument(variable, excludes, context, false);
  }

  public static boolean variableIsPassedAsMethodArgument(PsiVariable variable, Set<String> excludes, @Nullable PsiElement context,
                                                         boolean builderPattern) {
    if (context == null) {
      return false;
    }
    final VariablePassedAsArgumentExcludedVisitor visitor = new VariablePassedAsArgumentExcludedVisitor(variable, excludes, builderPattern);
    context.accept(visitor);
    return visitor.isPassed();
  }

  public static boolean variableIsUsedInArrayInitializer(PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableUsedInArrayInitializerVisitor visitor = new VariableUsedInArrayInitializerVisitor(variable);
    context.accept(visitor);
    return visitor.isPassed();
  }

  public static boolean variableIsAssigned(PsiVariable variable, @Nullable PsiElement context) {
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
  public static boolean variableIsAssigned(PsiVariable variable) {
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

  public static boolean variableIsAssigned(PsiVariable variable, @Nullable PsiElement context, boolean recurseIntoClasses) {
    if (context == null) {
      return false;
    }
    final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variable, recurseIntoClasses);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  public static boolean variableIsReturned(PsiVariable variable, @Nullable PsiElement context) {
    return variableIsReturned(variable, context, false);
  }

  public static boolean variableIsReturned(PsiVariable variable, @Nullable PsiElement context, boolean builderPattern) {
    if (context == null) {
      return false;
    }
    final VariableReturnedVisitor visitor = new VariableReturnedVisitor(variable, builderPattern);
    context.accept(visitor);
    return visitor.isReturned();
  }

  public static boolean variableValueIsUsed(PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableValueUsedVisitor visitor = new VariableValueUsedVisitor(variable);
    context.accept(visitor);
    return visitor.isVariableValueUsed();
  }

  public static boolean arrayContentsAreAccessed(PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final ArrayContentsAccessedVisitor visitor = new ArrayContentsAccessedVisitor(variable);
    context.accept(visitor);
    return visitor.isAccessed();
  }

  public static boolean arrayContentsAreAssigned(PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final ArrayContentsAssignedVisitor visitor = new ArrayContentsAssignedVisitor(variable);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  public static boolean variableIsUsedInInnerClass(PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableUsedInInnerClassVisitor visitor = new VariableUsedInInnerClassVisitor(variable);
    context.accept(visitor);
    return visitor.isUsedInInnerClass();
  }

  public static boolean mayEvaluateToVariable(@Nullable PsiExpression expression, PsiVariable variable) {
    return mayEvaluateToVariable(expression, variable, false);
  }

  public static List<PsiReferenceExpression> getVariableReferences(PsiVariable variable, @Nullable PsiElement context) {
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

  public static boolean mayEvaluateToVariable(@Nullable PsiExpression expression, PsiVariable variable, boolean builderPattern) {
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

  public static boolean evaluatesToVariable(@Nullable PsiExpression expression, PsiVariable variable) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
    final PsiElement target = referenceExpression.resolve();
    return variable.equals(target);
  }

  public static boolean variableIsUsed(PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableUsedVisitor visitor = new VariableUsedVisitor(variable);
    context.accept(visitor);
    return visitor.isUsed();
  }

  public static boolean variableIsDecremented(PsiVariable variable, @Nullable PsiStatement statement) {
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

  public static boolean variableIsIncremented(PsiVariable variable, @Nullable PsiStatement statement) {
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

  public static boolean variableIsAssignedBeforeReference(PsiReferenceExpression referenceExpression, @Nullable PsiElement context) {
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

  public static boolean variableIsAssignedAtPoint(PsiVariable variable, @Nullable PsiElement context, PsiElement point) {
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
  private static PsiElement getDirectChildWhichContainsElement(PsiElement ancestor, PsiElement descendant) {
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

  public static boolean isAnyVariableAssigned(Collection<PsiVariable> variables, @Nullable PsiElement context) {
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


  /**
   * Finds all references to the specified variable in the declaration scope of the variable,
   * i.e. everywhere the variable is accessible.
   * NOTE: this method will only search in the containing file for the variable. This may lead to incorrect results for fields.
   *
   * @param variable  the variable to find references for
   * @return a list of references, empty list if no references were found.
   */
  public static List<PsiReferenceExpression> getVariableReferences(PsiVariable variable) {
    PsiFile file = variable.getContainingFile();
    if (file == null) return List.of();
    // Consulo has no LocalRefUseInfo (the cache of the highlighting pass), the references are searched in the scope of the variable
    return getVariableReferences(variable, variable instanceof PsiField ? file : PsiUtil.getVariableCodeBlock(variable, null));
  }


  /**
   * @param statement statement to scan
   * @return list of variables declared inside given element that could conflict with other declarations on statement level.
   * I.e. all local and pattern declarations declared inside, except declarations from the local/anonymous classes.
   */
  public static List<PsiVariable> findDeclaredVariables(PsiStatement statement) {
    List<PsiVariable> variables = new ArrayList<>();
    statement.accept(new JavaRecursiveElementWalkingVisitor() {

      @Override
      public void visitClass(final PsiClass aClass) {}

      @Override
      public void visitVariable(PsiVariable variable) {
        variables.add(variable);
        super.visitVariable(variable);
      }
    });
    return variables;
  }


  /// Returns true if the type of element that `reference` refers to can be safely changed to `targetType`.
  ///
  /// ### Example
  ///
  /// Consider the following (obviously wrong, but good enough for demo purpose) code:
  ///
  /// ```java
  /// void foo(Optional<String> opt) {
  ///   Object obj = opt.get();
  ///   if (obj instanceof Integer) {
  ///     doStuff();
  ///   }
  ///   // ...
  /// }
  /// ```
  ///
  /// Let's say you'd like to know if it's "safe" to change the type of local variable `obj` from `Object` to `String`.
  /// "Safe" means that there likely will be no compile-time errors or behavior difference after such a type change is performed.
  ///
  /// In our example, this method returns false because changing the type of `obj`
  /// to `String` will cause a compile-time error like `cannot cast 'java.lang.String' to 'java.lang.Integer'`.
  ///
  /// It's possible there are cases not covered by this method.
  /// If you discover such a case, consider updating the implementation.
  public static boolean isVariableTypeChangeSafeForReference(PsiType targetType, PsiReferenceExpression reference) {
    if (targetType instanceof PsiLambdaParameterType || PsiTypes.nullType().equals(targetType)) return false;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(reference.getParent());
    if (PsiUtil.isAccessedForWriting(reference)) {
      if (!(parent instanceof PsiAssignmentExpression assignment)) return false;
      PsiExpression rValue = assignment.getRExpression();
      if (rValue == null) return false;
      PsiType rValueType = rValue.getType();
      return rValueType != null && targetType.isAssignableFrom(rValueType);
    }
    while (parent instanceof PsiConditionalExpression) {
      parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    }
    if (parent instanceof PsiInstanceOfExpression instanceOf) {
      PsiTypeElement checkTypeElement = instanceOf.getCheckType();
      if (checkTypeElement == null) return false;
      PsiType checkType = checkTypeElement.getType();
      // Could be always false instanceof which will become compilation error after fix
      return TypeConversionUtil.areTypesConvertible(targetType, checkType);
    }
    if (parent instanceof PsiTypeCastExpression parentCast) {
      PsiTypeElement castTypeElement = parentCast.getCastType();
      if (castTypeElement == null) return false;
      PsiType castType = castTypeElement.getType();
      // Another replacement could become invalid due to this change
      return TypeConversionUtil.areTypesConvertible(targetType, castType);
    }
    // Some method call can be mis-resolved after update, check this
    if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression call) {
      PsiMethod method = call.resolveMethod();
      if (method == null) return false;
      PsiCallExpression callCopy = (PsiCallExpression)call.copy();
      PsiElement refCopy = PsiTreeUtil.findSameElementInCopy(reference, call, callCopy);
      if (refCopy == null) return false;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());
      PsiTypeCastExpression insertedCast = (PsiTypeCastExpression)refCopy.replace(
        factory.createExpressionFromText("(a)"+reference.getReferenceName(), refCopy));
      Objects.requireNonNull(insertedCast.getCastType()).replace(factory.createTypeElement(targetType));
      return callCopy.resolveMethod() == method;
    }
    if (parent instanceof PsiReferenceExpression ref) {
      final PsiElement resolve = ref.resolve();
      // private member cannot be accessed on a subtype qualifier
      if (resolve instanceof PsiMember member && member.hasModifierProperty(PsiModifier.PRIVATE)) {
        return false;
      }
    }
    return true;
  }
}
