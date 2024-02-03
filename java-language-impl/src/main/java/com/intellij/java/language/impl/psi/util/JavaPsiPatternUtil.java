// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.util;

import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.impl.psi.impl.source.JavaVarTypeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class JavaPsiPatternUtil {
  public static @Nullable PsiType getDeconstructedImplicitPatternVariableType(@Nonnull PsiPatternVariable parameter) {
    return getDeconstructedImplicitPatternType(parameter.getPattern());
  }

  /**
   * @return type of variable in pattern, or null if pattern is incomplete
   */
  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static PsiType getPatternType(@Nullable PsiCaseLabelElement pattern) {
    PsiTypeElement typeElement = getPatternTypeElement(pattern);
    if (typeElement == null) return null;
    return typeElement.getType();
  }

  /**
   * @param pattern deconstruction pattern to find a context type for
   * @return a context type for the pattern; null, if it cannot be determined. This method can perform
   * the inference for outer patterns if necessary.
   */
  @Nullable
  public static PsiType getContextType(@Nonnull PsiPattern pattern) {
    PsiElement parent = pattern.getParent();
    while (parent instanceof PsiParenthesizedPattern) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiInstanceOfExpression) {
      return ((PsiInstanceOfExpression)parent).getOperand().getType();
    }
    if (parent instanceof PsiForeachPatternStatement) {
      PsiExpression iteratedValue = ((PsiForeachPatternStatement)parent).getIteratedValue();
      if (iteratedValue == null) {
        return null;
      }
      return JavaGenericsUtil.getCollectionItemType(iteratedValue);
    }
    if (parent instanceof PsiCaseLabelElementList) {
      PsiSwitchLabelStatementBase label = ObjectUtil.tryCast(parent.getParent(), PsiSwitchLabelStatementBase.class);
      if (label != null) {
        PsiSwitchBlock block = label.getEnclosingSwitchBlock();
        if (block != null) {
          PsiExpression expression = block.getExpression();
          if (expression != null) {
            return expression.getType();
          }
        }
      }
    }
    if (parent instanceof PsiDeconstructionList) {
      PsiDeconstructionPattern parentPattern = ObjectUtil.tryCast(parent.getParent(), PsiDeconstructionPattern.class);
      if (parentPattern != null) {
        int index = ArrayUtil.indexOf(((PsiDeconstructionList)parent).getDeconstructionComponents(), pattern);
        if (index < 0) return null;
        PsiType patternType = parentPattern.getTypeElement().getType();
        if (!(patternType instanceof PsiClassType)) return null;
        PsiSubstitutor parentSubstitutor = ((PsiClassType)patternType).resolveGenerics().getSubstitutor();
        PsiClass parentRecord = PsiUtil.resolveClassInClassTypeOnly(parentPattern.getTypeElement().getType());
        if (parentRecord == null) return null;
        PsiRecordComponent[] components = parentRecord.getRecordComponents();
        if (index >= components.length) return null;
        return parentSubstitutor.substitute(components[index].getType());
      }
    }
    return null;
  }

  public static @Nullable PsiTypeElement getPatternTypeElement(@Nullable PsiCaseLabelElement pattern) {
    if (pattern == null) return null;
    if (pattern instanceof PsiParenthesizedPattern) {
      return getPatternTypeElement(((PsiParenthesizedPattern)pattern).getPattern());
    }
    else if (pattern instanceof PsiDeconstructionPattern) {
      return ((PsiDeconstructionPattern)pattern).getTypeElement();
    }
    else if (pattern instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)pattern).getCheckType();
    }
    else if (pattern instanceof PsiUnnamedPattern) {
      return ((PsiUnnamedPattern)pattern).getTypeElement();
    }
    return null;
  }

  public static @Nullable PsiType getDeconstructedImplicitPatternType(@Nonnull PsiPattern pattern) {
    PsiRecordComponent recordComponent = getRecordComponentForPattern(pattern);
    if (recordComponent != null) {
      PsiDeconstructionList deconstructionList = ObjectUtil.tryCast(pattern.getParent(), PsiDeconstructionList.class);
      if (deconstructionList == null) return null;
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)deconstructionList.getParent();
      PsiType patternType = deconstructionPattern.getTypeElement().getType();
      if (patternType instanceof PsiClassType) {
        patternType = PsiUtil.captureToplevelWildcards(patternType, pattern);
        PsiSubstitutor substitutor = ((PsiClassType)patternType).resolveGenerics().getSubstitutor();
        PsiType recordComponentType = recordComponent.getType();
        return JavaVarTypeUtil.getUpwardProjection(substitutor.substitute(recordComponentType));
      }
    }
    return null;
  }

  @Contract(pure = true)
  @Nullable
  public static PsiRecordComponent getRecordComponentForPattern(@Nonnull PsiPattern pattern) {
    PsiDeconstructionList deconstructionList = ObjectUtil.tryCast(pattern.getParent(), PsiDeconstructionList.class);
    if (deconstructionList == null) return null;
    @Nonnull PsiPattern[] patterns = deconstructionList.getDeconstructionComponents();
    int index = ArrayUtil.indexOf(patterns, pattern);
    PsiDeconstructionPattern deconstructionPattern = ObjectUtil.tryCast(deconstructionList.getParent(), PsiDeconstructionPattern.class);
    if (deconstructionPattern == null) return null;
    PsiClassType classType = ObjectUtil.tryCast(deconstructionPattern.getTypeElement().getType(), PsiClassType.class);
    if (classType == null) return null;
    PsiClass aClass = classType.resolve();
    if (aClass == null) return null;
    PsiRecordComponent[] components = aClass.getRecordComponents();
    if (components.length <= index) return null;
    return components[index];
  }

  /**
   * @param expression expression to search pattern variables in
   * @return list of pattern variables declared within an expression that could be visible outside of given expression.
   */
  @Contract(pure = true)
  public static
  @Nonnull
  List<PsiPatternVariable> getExposedPatternVariables(@Nonnull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    boolean parentMayAccept =
      parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationTokenType().equals(JavaTokenType.EXCL) ||
        parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.ANDAND) ||
        parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.OROR) ||
        parent instanceof PsiConditionalExpression || parent instanceof PsiIfStatement || parent instanceof PsiConditionalLoopStatement;
    if (!parentMayAccept) {
      return Collections.emptyList();
    }
    List<PsiPatternVariable> list = new ArrayList<>();
    collectPatternVariableCandidates(expression, expression, list, false);
    return list;
  }

  /**
   * @param expression expression to search pattern variables in
   * @return list of pattern variables declared within an expression that could be visible outside of given expression
   * under some other parent (e.g. under PsiIfStatement).
   */
  @Contract(pure = true)
  @jakarta.annotation.Nonnull
  public static List<PsiPatternVariable> getExposedPatternVariablesIgnoreParent(@jakarta.annotation.Nonnull PsiExpression expression) {
    List<PsiPatternVariable> list = new ArrayList<>();
    collectPatternVariableCandidates(expression, expression, list, true);
    return list;
  }

  /**
   * @param variable pattern variable
   * @return effective initializer expression for the variable; null if cannot be determined
   */
  @Nullable
  public static String getEffectiveInitializerText(@Nonnull PsiPatternVariable variable) {
    PsiPattern pattern = variable.getPattern();
    PsiInstanceOfExpression instanceOf = ObjectUtil.tryCast(pattern.getParent(), PsiInstanceOfExpression.class);
    if (instanceOf == null) {
      return null;
    }
    if (pattern instanceof PsiTypeTestPattern) {
      PsiExpression operand = instanceOf.getOperand();
      PsiTypeElement checkType = ((PsiTypeTestPattern)pattern).getCheckType();
      if (checkType.getType().equals(operand.getType())) {
        return operand.getText();
      }
      return "(" + checkType.getText() + ")" + operand.getText();
    }
    return null;
  }

  private static void collectPatternVariableCandidates(@Nonnull PsiExpression scope, @Nonnull PsiExpression expression,
                                                       Collection<PsiPatternVariable> candidates, boolean strict) {
    while (true) {
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
      }
      else if (expression instanceof PsiPrefixExpression &&
        ((PsiPrefixExpression)expression).getOperationTokenType().equals(JavaTokenType.EXCL)) {
        expression = ((PsiPrefixExpression)expression).getOperand();
      }
      else {
        break;
      }
    }
    if (expression instanceof PsiInstanceOfExpression) {
      PsiPattern pattern = ((PsiInstanceOfExpression)expression).getPattern();
      if (pattern instanceof PsiTypeTestPattern) {
        PsiPatternVariable variable = ((PsiTypeTestPattern)pattern).getPatternVariable();
        if (variable != null && !PsiTreeUtil.isAncestor(scope, variable.getDeclarationScope(), strict)) {
          candidates.add(variable);
        }
      }
    }
    if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          collectPatternVariableCandidates(scope, operand, candidates, strict);
        }
      }
    }
  }
}
