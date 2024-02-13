// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.psi.impl.source.resolve.reference.impl;

import com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Set;

import static com.intellij.java.language.psi.util.PsiUtil.skipParenthesizedExprDown;
import static com.intellij.java.language.psi.util.PsiUtil.skipParenthesizedExprUp;
import static consulo.language.psi.search.PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES;

@ExtensionImpl
public final class AtomicReferenceImplicitUsageProvider implements ImplicitUsageProvider {
  private static final Set<String> ourUpdateMethods = Set.of(
    "compareAndSet", "weakCompareAndSet", "set", "lazySet", "getAndSet", "getAndIncrement", "getAndDecrement", "getAndAdd",
    "incrementAndGet", "decrementAndGet", "addAndGet", "getAndUpdate", "updateAndGet", "getAndAccumulate", "accumulateAndGet");

  @Override
  public boolean isImplicitUsage(@Nonnull PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitRead(@Nonnull PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(@Nonnull PsiElement element) {
    if (element instanceof PsiField field && field.hasModifierProperty(PsiModifier.VOLATILE)) {
      return LanguageCachedValueUtil.getCachedValue(field, () ->
        new CachedValueProvider.Result<>(isAtomicWrite(field), PsiModificationTracker.MODIFICATION_COUNT));
    }                         
    return false;
  }

  private static boolean isAtomicWrite(@Nonnull PsiField field) {
    PsiType type = field.getType();
    if (PsiTypes.intType().equals(type)) {
      return isAtomicWrite(field, JavaReflectionReferenceUtil.ATOMIC_INTEGER_FIELD_UPDATER);
    }
    if (PsiTypes.longType().equals(type)) {
      return isAtomicWrite(field, JavaReflectionReferenceUtil.ATOMIC_LONG_FIELD_UPDATER);
    }
    if (!(type instanceof PsiPrimitiveType)) {
      return isAtomicWrite(field, JavaReflectionReferenceUtil.ATOMIC_REFERENCE_FIELD_UPDATER);
    }
    return false;
  }

  private static boolean isAtomicWrite(@Nonnull PsiField field, @NonNls String updaterName) {
    SearchScope scope = getCheapSearchScope(field);
    if (scope == null) {
      return false;
    }
    Query<PsiReference> fieldQuery = ReferencesSearch.search(field, scope);
    return !fieldQuery.forEach((PsiReference reference) -> findAtomicUpdaters(reference, updaterName));
  }

  private static boolean findAtomicUpdaters(@Nonnull PsiReference reference, @Nonnull String updaterName) {
    if (!(reference instanceof JavaLangClassMemberReference)) { // optimization
      return true;
    }
    PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethodCallExpression.class);
    if (methodCall == null || !JavaReflectionReferenceUtil.isCallToMethod(methodCall, updaterName, JavaReflectionReferenceUtil.NEW_UPDATER)) {
      return true;
    }
    PsiElement callParent = skipParenthesizedExprUp(methodCall.getParent());
    PsiVariable updaterVariable = null;
    if (callParent instanceof PsiVariable var && skipParenthesizedExprDown(var.getInitializer()) == methodCall) {
      updaterVariable = var;
    }
    else if (callParent instanceof PsiAssignmentExpression assignment) {
      if (assignment.getOperationTokenType() == JavaTokenType.EQ &&
        skipParenthesizedExprDown(assignment.getRExpression()) == methodCall &&
        skipParenthesizedExprDown(assignment.getLExpression()) instanceof PsiReferenceExpression refExpr &&
        refExpr.resolve() instanceof PsiVariable var) {
        updaterVariable = var;
      }
    }
    if (updaterVariable != null && InheritanceUtil.isInheritor(updaterVariable.getType(), updaterName)) {
      Query<PsiReference> updaterQuery = ReferencesSearch.search(updaterVariable);
      if (!updaterQuery.forEach(AtomicReferenceImplicitUsageProvider::findWrites)) {
        return false;
      }
    }
    return true;
  }

  private static boolean findWrites(@Nonnull PsiReference reference) {
    PsiElement element = reference.getElement();
    PsiReferenceExpression methodExpression =
      ObjectUtil.tryCast(skipParenthesizedExprUp(element.getParent()), PsiReferenceExpression.class);
    if (methodExpression != null &&
      (methodExpression instanceof PsiMethodReferenceExpression || methodExpression.getParent() instanceof PsiMethodCallExpression) &&
      methodExpression.getReferenceName() != null &&
      ourUpdateMethods.contains(methodExpression.getReferenceName()) &&
      skipParenthesizedExprDown(methodExpression.getQualifierExpression()) == element) {

      return false;
    }
    return true;
  }

  @Nullable
  private static SearchScope getCheapSearchScope(@Nonnull PsiField field) {
    SearchScope scope = field.getUseScope();
    if (scope instanceof LocalSearchScope) {
      return scope;
    }

    String name = field.getName();
    Project project = field.getProject();
    PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);

    if (scope instanceof GlobalSearchScope &&
      searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)scope, null, null) == FEW_OCCURRENCES) {
      return scope;
    }
    return null;
  }
}
