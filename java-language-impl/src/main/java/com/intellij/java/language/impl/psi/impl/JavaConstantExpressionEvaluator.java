/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Maps;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class JavaConstantExpressionEvaluator extends JavaRecursiveElementWalkingVisitor {
  private final Supplier<ConcurrentMap<PsiElement, Object>> myMapFactory;
  private final Project myProject;

  private static final Key<CachedValue<ConcurrentMap<PsiElement,Object>>> CONSTANT_VALUE_WO_OVERFLOW_MAP_KEY = Key.create("CONSTANT_VALUE_WO_OVERFLOW_MAP_KEY");
  private static final Key<CachedValue<ConcurrentMap<PsiElement,Object>>> CONSTANT_VALUE_WITH_OVERFLOW_MAP_KEY = Key.create("CONSTANT_VALUE_WITH_OVERFLOW_MAP_KEY");
  private static final Object NO_VALUE = ObjectUtil.NULL;
  private final ConstantExpressionVisitor myConstantExpressionVisitor;

  private JavaConstantExpressionEvaluator(Set<PsiVariable> visitedVars, final boolean throwExceptionOnOverflow, final Project project, final PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    myMapFactory = auxEvaluator != null ? new Supplier<ConcurrentMap<PsiElement, Object>>() {
      @Override
      public ConcurrentMap<PsiElement, Object> get() {
        return auxEvaluator.getCacheMap(throwExceptionOnOverflow);
      }
    } : new Supplier<ConcurrentMap<PsiElement, Object>>() {
      @Override
      public ConcurrentMap<PsiElement, Object> get() {
        final Key<CachedValue<ConcurrentMap<PsiElement, Object>>> key =
          throwExceptionOnOverflow ? CONSTANT_VALUE_WITH_OVERFLOW_MAP_KEY : CONSTANT_VALUE_WO_OVERFLOW_MAP_KEY;
        return CachedValuesManager.getManager(myProject).getCachedValue(myProject, key, PROVIDER, false);
      }
    };
    myProject = project;
    myConstantExpressionVisitor = new ConstantExpressionVisitor(visitedVars, throwExceptionOnOverflow, auxEvaluator);

  }

  @Override
  protected void elementFinished(PsiElement element) {
    Object value = getCached(element);
    if (value == null) {
      Object result = myConstantExpressionVisitor.handle(element);
      cache(element, result);
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    Object value = getCached(element);
    if (value == null) {
      super.visitElement(element);
      // will cache back in elementFinished()
    }
    else {
      ConstantExpressionVisitor.store(element, value == NO_VALUE ? null : value);
    }
  }

  private static final CachedValueProvider<ConcurrentMap<PsiElement,Object>> PROVIDER = new CachedValueProvider<ConcurrentMap<PsiElement,Object>>() {
    @Override
    public Result<ConcurrentMap<PsiElement,Object>> compute() {
      ConcurrentMap<PsiElement, Object> value = ContainerUtil.createConcurrentSoftMap();
      return Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
    }
  };

  private Object getCached(@Nonnull PsiElement element) {
    return map().get(element);
  }
  private Object cache(@Nonnull PsiElement element, @Nullable Object value) {
    value = Maps.cacheOrGet(map(), element, value == null ? NO_VALUE : value);
    if (value == NO_VALUE) {
      value = null;
    }
    return value;
  }

  @Nonnull
  private ConcurrentMap<PsiElement, Object> map() {
    return myMapFactory.get();
  }

  public static Object computeConstantExpression(PsiExpression expression, @Nullable Set<PsiVariable> visitedVars, boolean throwExceptionOnOverflow) {
    return computeConstantExpression(expression, visitedVars, throwExceptionOnOverflow, null);
  }

  public static Object computeConstantExpression(PsiExpression expression, @Nullable Set<PsiVariable> visitedVars, boolean throwExceptionOnOverflow,
                                                 final PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    if (expression == null) return null;

    JavaConstantExpressionEvaluator evaluator = new JavaConstantExpressionEvaluator(visitedVars, throwExceptionOnOverflow, expression.getProject(), auxEvaluator);

    if (expression instanceof PsiCompiledElement) {
      // in case of compiled elements we are not allowed to use PSI walking
      // but really in Cls there are only so many cases to handle
      if (expression instanceof PsiPrefixExpression) {
        PsiElement operand = ((PsiPrefixExpression)expression).getOperand();
        if (operand == null) return null;
        Object value = evaluator.myConstantExpressionVisitor.handle(operand);
        ConstantExpressionVisitor.store(operand, value);
      }
      return evaluator.myConstantExpressionVisitor.handle(expression);
    }
    expression.accept(evaluator);
    Object cached = evaluator.getCached(expression);
    return cached == NO_VALUE ? null : cached;
  }
  
  public static Object computeConstantExpression(PsiExpression expression, boolean throwExceptionOnOverflow) {
    return computeConstantExpression(expression, null, throwExceptionOnOverflow);
  }
}
