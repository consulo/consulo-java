/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import consulo.util.collection.ArrayFactory;

import jakarta.annotation.Nullable;
import java.util.function.Function;

/**
 * Represents a Java expression.
 */
public interface PsiExpression extends PsiAnnotationMemberValue, PsiCaseLabelElement {
  /**
   * The empty array of PSI expressions which can be reused to avoid unnecessary allocations.
   */
  PsiExpression[] EMPTY_ARRAY = new PsiExpression[0];

  ArrayFactory<PsiExpression> ARRAY_FACTORY = count -> count == 0 ? PsiExpression.EMPTY_ARRAY : new PsiExpression[count];

  Function<PsiExpression, PsiType> EXPRESSION_TO_TYPE = PsiExpression::getType;

  /**
   * Returns the type of the expression.
   *
   * @return the expression type, or null if the type is not known.
   */
  @Nullable
  PsiType getType();
}
