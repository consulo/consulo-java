/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.language.psi.PsiExpression;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

public class ForAscendingPostfixTemplate extends ForIndexedPostfixTemplate {
  public ForAscendingPostfixTemplate() {
    super("fori", "for (int i = 0; i < expr.length; i++)");
  }

  @Override
  @jakarta.annotation.Nonnull
  protected String getOperator() {
    return "++";
  }

  @Nonnull
  @Override
  protected String getComparativeSign(@jakarta.annotation.Nonnull PsiExpression expr) {
    return "<";
  }

  @Nullable
  @Override
  protected Pair<String, String> calculateBounds(@jakarta.annotation.Nonnull PsiExpression expression) {
    String bound = getExpressionBound(expression);
    return bound != null ? Pair.create("0", bound) : null;
  }
}