// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.codeInsight.daemon.JavaImplicitUsageProvider;
import com.intellij.java.analysis.impl.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaExpressionFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.application.util.CachedValueProvider;
import consulo.content.scope.SearchScope;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

public final class NullabilityUtil {

  @Nonnull
  public static DfaNullability calcCanBeNull(DfaVariableValue value) {
    if (value.getDescriptor() instanceof DfaExpressionFactory.ThisDescriptor) {
      return DfaNullability.NOT_NULL;
    }
    if (value.getDescriptor() == SpecialField.OPTIONAL_VALUE) {
      return DfaNullability.NULLABLE;
    }
    PsiModifierListOwner var = value.getPsiVariable();
    if (value.getType() instanceof PsiPrimitiveType) {
      return DfaNullability.UNKNOWN;
    }
    Nullability nullability = DfaPsiUtil.getElementNullabilityIgnoringParameterInference(value.getType(), var);
    if (nullability != Nullability.UNKNOWN) {
      return DfaNullability.fromNullability(nullability);
    }
    if (var == null) {
      return DfaNullability.UNKNOWN;
    }

    Nullability defaultNullability = value.getFactory().suggestNullabilityForNonAnnotatedMember(var);

    if (var instanceof PsiParameter && var.getParent() instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement) var.getParent()).getIteratedValue();
      if (iteratedValue != null) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
        if (itemType != null) {
          return DfaNullability.fromNullability(DfaPsiUtil.getElementNullability(itemType, var));
        }
      }
    }

    if (var instanceof PsiField && value.getFactory().canTrustFieldInitializer((PsiField) var)) {
      return DfaNullability.fromNullability(getNullabilityFromFieldInitializers((PsiField) var, defaultNullability).second);
    }

    return DfaNullability.fromNullability(defaultNullability);
  }

  static Pair<PsiExpression, Nullability> getNullabilityFromFieldInitializers(PsiField field, Nullability defaultNullability) {
    if (DfaPsiUtil.isFinalField(field) && PsiAugmentProvider.canTrustFieldInitializer(field)) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null) {
        return Pair.create(initializer, getExpressionNullability(initializer));
      }

      List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
      if (initializers.isEmpty()) {
        return Pair.create(null, defaultNullability);
      }

      for (PsiExpression expression : initializers) {
        Nullability nullability = getExpressionNullability(expression);
        if (nullability == Nullability.NULLABLE) {
          return Pair.create(expression, Nullability.NULLABLE);
        }
      }

      if (DfaPsiUtil.isInitializedNotNull(field)) {
        return Pair.create(ContainerUtil.getOnlyItem(initializers), Nullability.NOT_NULL);
      }
    } else if (isOnlyImplicitlyInitialized(field)) {
      return Pair.create(null, Nullability.NOT_NULL);
    }
    return Pair.create(null, defaultNullability);
  }

  private static boolean isOnlyImplicitlyInitialized(PsiField field) {
    return LanguageCachedValueUtil.getCachedValue(field, () -> CachedValueProvider.Result.create(
        isImplicitlyInitializedNotNull(field) && weAreSureThereAreNoExplicitWrites(field),
        PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static boolean isImplicitlyInitializedNotNull(PsiField field) {
    Project project = field.getProject();
    return project.getExtensionPoint(ImplicitUsageProvider.class).findFirstSafe(provider -> {
      return provider instanceof JavaImplicitUsageProvider javaImplicitUsageProvider && javaImplicitUsageProvider.isImplicitlyNotNullInitialized(field);
    }) != null;
  }

  private static boolean weAreSureThereAreNoExplicitWrites(PsiField field) {
    String name = field.getName();
    if (field.getInitializer() != null) {
      return false;
    }

    if (!isCheapEnoughToSearch(field, name)) {
      return false;
    }

    return ReferencesSearch.search(field).allMatch(
        reference -> reference instanceof PsiReferenceExpression && !PsiUtil.isAccessedForWriting((PsiReferenceExpression) reference));
  }

  private static boolean isCheapEnoughToSearch(PsiField field, String name) {
    SearchScope scope = field.getUseScope();
    if (!(scope instanceof GlobalSearchScope)) {
      return true;
    }

    PsiSearchHelper helper = PsiSearchHelper.getInstance(field.getProject());
    PsiSearchHelper.SearchCostResult result =
        helper.isCheapEnoughToSearch(name, (GlobalSearchScope) scope, field.getContainingFile(), null);
    return result != PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
  }

  public static Nullability getExpressionNullability(@Nullable PsiExpression expression) {
    return getExpressionNullability(expression, false);
  }

  /**
   * Tries to determine an expression nullability
   *
   * @param expression  an expression to check
   * @param useDataflow whether to use dataflow (more expensive, but may produce more precise result)
   * @return expression nullability. UNKNOWN if unable to determine;
   * NULLABLE if known to possibly have null value; NOT_NULL if definitely never null.
   */
  public static Nullability getExpressionNullability(@Nullable PsiExpression expression, boolean useDataflow) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) {
      return Nullability.UNKNOWN;
    }
    if (PsiType.NULL.equals(expression.getType())) {
      return Nullability.NULLABLE;
    }
    if (expression instanceof PsiNewExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiPolyadicExpression ||
        expression instanceof PsiFunctionalExpression ||
        expression.getType() instanceof PsiPrimitiveType) {
      return Nullability.NOT_NULL;
    }
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression) expression).getThenExpression();
      PsiExpression elseExpression = ((PsiConditionalExpression) expression).getElseExpression();
      if (thenExpression == null || elseExpression == null) {
        return Nullability.UNKNOWN;
      }
      PsiExpression condition = ((PsiConditionalExpression) expression).getCondition();
      // simple cases like x == null ? something : x
      PsiReferenceExpression ref = ExpressionUtils.getReferenceExpressionFromNullComparison(condition, true);
      if (ref != null && JavaPsiEquivalenceUtil.areExpressionsEquivalent(ref, elseExpression)) {
        return getExpressionNullability(thenExpression, useDataflow);
      }
      // x != null ? x : something
      ref = ExpressionUtils.getReferenceExpressionFromNullComparison(condition, false);
      if (ref != null && JavaPsiEquivalenceUtil.areExpressionsEquivalent(ref, thenExpression)) {
        return getExpressionNullability(elseExpression, useDataflow);
      }
      if (useDataflow) {
        return DfaNullability.toNullability(DfaNullability.fromDfType(CommonDataflow.getDfType(expression)));
      }
      Nullability left = getExpressionNullability(thenExpression, false);
      if (left == Nullability.UNKNOWN) {
        return Nullability.UNKNOWN;
      }
      Nullability right = getExpressionNullability(elseExpression, false);
      return left == right ? left : Nullability.UNKNOWN;
    }
    if (expression instanceof PsiTypeCastExpression) {
      return getExpressionNullability(((PsiTypeCastExpression) expression).getOperand(), useDataflow);
    }
    if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression) expression;
      if (assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        return getExpressionNullability(assignment.getRExpression(), useDataflow);
      }
      return Nullability.NOT_NULL;
    }
    if (useDataflow) {
      return DfaNullability.toNullability(DfaNullability.fromDfType(CommonDataflow.getDfType(expression)));
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression) expression;
      PsiElement target = (ref).resolve();
      if (target instanceof PsiPatternVariable) {
        return Nullability.NOT_NULL; // currently all pattern variables are not-null
      }
      if (target instanceof PsiLocalVariable || target instanceof PsiParameter) {
        PsiElement block = PsiUtil.getVariableCodeBlock((PsiVariable) target, null);
        // Do not trust the declared nullability of local variable/parameter if it's reassigned as nullability designates
        // only initial nullability
        if (block == null || !HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable) target, block, ref)) {
          return Nullability.UNKNOWN;
        }
      }
      return DfaPsiUtil.getElementNullabilityIgnoringParameterInference(expression.getType(), (PsiModifierListOwner) target);
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression) expression).resolveMethod();
      return method != null ? DfaPsiUtil.getElementNullability(expression.getType(), method) : Nullability.UNKNOWN;
    }
    return Nullability.UNKNOWN;
  }
}
