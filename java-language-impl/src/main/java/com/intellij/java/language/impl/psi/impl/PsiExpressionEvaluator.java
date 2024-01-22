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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiConstantEvaluationHelper;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class PsiExpressionEvaluator implements ConstantExpressionEvaluator {

  @Override
  public Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow) {
    return expression instanceof PsiExpression ? JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)expression, throwExceptionOnOverflow) : null;
  }

  @Override
  public Object computeExpression(PsiElement expression, boolean throwExceptionOnOverflow, @Nullable PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    return expression instanceof PsiExpression ? JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)expression, null, throwExceptionOnOverflow, auxEvaluator) : null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
