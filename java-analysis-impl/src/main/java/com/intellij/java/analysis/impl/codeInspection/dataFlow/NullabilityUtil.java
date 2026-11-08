// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import consulo.language.dataFlow.types.DfaNullability;
import com.intellij.java.analysis.codeInsight.daemon.JavaImplicitUsageProvider;
import com.intellij.java.language.codeInsight.DataflowNullabilityContributor;
import com.intellij.java.analysis.impl.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.Pair;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiCall;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiDeconstructionList;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiPatternVariable;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiTemplateExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.psi.TypeAnnotationProvider;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowUtil;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.PsiModificationTracker;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.util.collection.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class NullabilityUtil {

  public static Pair<PsiExpression, Nullability> getNullabilityFromFieldInitializers(PsiField field) {
    if (DfaPsiUtil.isFinalField(field) && PsiAugmentProvider.canTrustFieldInitializer(field)) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null) {
        return Pair.create(initializer, getExpressionNullability(initializer));
      }

      List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
      if (initializers.isEmpty()) {
        return Pair.create(null, Nullability.UNKNOWN);
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
    }
    else if (isOnlyImplicitlyInitialized(field)) {
      return Pair.create(null, Nullability.NOT_NULL);
    }
    return Pair.create(null, Nullability.UNKNOWN);
  }

  private static boolean isOnlyImplicitlyInitialized(PsiField field) {
    return LanguageCachedValueUtil.getCachedValue(field, () -> CachedValueProvider.Result.create(
      isImplicitlyInitializedNotNull(field) && weAreSureThereAreNoExplicitWrites(field),
      PsiModificationTracker.MODIFICATION_COUNT));
  }

  public static boolean isImplicitlyInitializedNotNull(PsiField field) {
    return ContainerUtil.exists(field.getProject().getExtensionList(ImplicitUsageProvider.class), p -> p instanceof JavaImplicitUsageProvider javaProvider && javaProvider.isImplicitlyNotNullInitialized(field)) ||
           DataflowNullabilityContributor.findNullability(field) == Nullability.NOT_NULL;
  }

  private static boolean weAreSureThereAreNoExplicitWrites(PsiField field) {
    if (field.hasInitializer()) return false;
    if (!field.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    return !VariableAccessUtils.variableIsAssigned(field);
  }

  public static Nullability getExpressionNullability(@Nullable PsiExpression expression) {
    return getExpressionNullability(expression, false);
  }

  /**
   * Tries to determine an expression nullability
   *
   * @param expression an expression to check
   * @param useDataflow whether to use dataflow (more expensive, but may produce more precise result)
   * @return expression nullability. UNKNOWN if unable to determine;
   * NULLABLE if known to possibly have null value; NOT_NULL if definitely never null.
   */
  public static Nullability getExpressionNullability(@Nullable PsiExpression expression, boolean useDataflow) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return Nullability.UNKNOWN;
    if (PsiTypes.nullType().equals(expression.getType())) return Nullability.NULLABLE;
    if (expression instanceof PsiNewExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiPolyadicExpression ||
        expression instanceof PsiFunctionalExpression ||
        expression.getType() instanceof PsiPrimitiveType) {
      return Nullability.NOT_NULL;
    }
    boolean dumb = DumbService.isDumb(expression.getProject());
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      if (thenExpression == null || elseExpression == null) return Nullability.UNKNOWN;
      PsiExpression condition = ((PsiConditionalExpression)expression).getCondition();
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
      if (useDataflow && !dumb) {
        return JavaDfaNullability.toNullability(DfaNullability.fromDfType(CommonDataflow.getDfType(expression)));
      }
      Nullability left = getExpressionNullability(thenExpression, false);
      if (left == Nullability.UNKNOWN) return Nullability.UNKNOWN;
      Nullability right = getExpressionNullability(elseExpression, false);
      return left == right ? left : Nullability.UNKNOWN;
    }
    if (expression instanceof PsiTypeCastExpression) {
      return getExpressionNullability(((PsiTypeCastExpression)expression).getOperand(), useDataflow);
    }
    if (expression instanceof PsiAssignmentExpression assignment) {
      if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        return getExpressionNullability(assignment.getRExpression(), useDataflow);
      }
      return Nullability.NOT_NULL;
    }
    if (useDataflow && !dumb) {
      return JavaDfaNullability.toNullability(DfaNullability.fromDfType(CommonDataflow.getDfType(expression)));
    }
    if (expression instanceof PsiReferenceExpression ref) {
      PsiElement target = ref.resolve();
      if (target instanceof PsiPatternVariable patternVariable && 
          !(patternVariable.getPattern().getParent() instanceof PsiDeconstructionList)) {
        return Nullability.NOT_NULL; // currently top-level pattern variables are not-null
      }
      if (dumb) return Nullability.UNKNOWN;
      if (target instanceof PsiLocalVariable || target instanceof PsiParameter) {
        PsiElement block = PsiUtil.getVariableCodeBlock((PsiVariable)target, null);
        // Do not trust the declared nullability of local variable/parameter if it's reassigned as nullability designates
        // only initial nullability
        if (block == null || !ControlFlowUtil.isEffectivelyFinal((PsiVariable)target, block)) return Nullability.UNKNOWN;
      }
      return DfaPsiUtil.getElementNullabilityForRead(expression.getType(), (PsiModifierListOwner)target);
    }
    if (expression instanceof PsiMethodCallExpression || expression instanceof PsiTemplateExpression) {
      if (dumb) return Nullability.UNKNOWN;
      PsiMethod method = ((PsiCall)expression).resolveMethod();
      return method != null ? DfaPsiUtil.getElementNullabilityForRead(expression.getType(), method) : Nullability.UNKNOWN;
    }
    return Nullability.UNKNOWN;
  }

  /**
   * @param type type to process
   * @return the same type but without top-level nullability annotations. Could be used to declare new local variables,
   * as top-level nullability annotations could be inferred from the initializer.
   */
  public static PsiType removeTopLevelNullabilityAnnotations(Project project, PsiType type) {
    PsiAnnotation[] annotations = type.getAnnotations();
    if (annotations.length == 0) return type;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    for (PsiAnnotation annotation : annotations) {
      if (manager.getAnnotationNullability(annotation.getQualifiedName()).isPresent()) {
        return type.annotate(TypeAnnotationProvider.Static.create(
          StreamEx.of(annotations).remove(a -> manager.getAnnotationNullability(a.getQualifiedName()).isPresent())
            .toArray(PsiAnnotation.EMPTY_ARRAY)));
      }
    }
    return type;
  }
}
