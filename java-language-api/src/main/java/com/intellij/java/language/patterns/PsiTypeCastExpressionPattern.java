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
package com.intellij.java.language.patterns;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.ElementPattern;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;

/**
 * @author nik
 */
public class PsiTypeCastExpressionPattern extends PsiExpressionPattern<PsiTypeCastExpression, PsiTypeCastExpressionPattern> {
  PsiTypeCastExpressionPattern() {
    super(PsiTypeCastExpression.class);
  }

  public PsiTypeCastExpressionPattern withOperand(final ElementPattern<? extends PsiExpression> operand) {
    return with(new PatternCondition<PsiTypeCastExpression>("withOperand") {
      @Override
      public boolean accepts(@Nonnull PsiTypeCastExpression psiTypeCastExpression, ProcessingContext context) {
        return operand.accepts(psiTypeCastExpression.getOperand(), context);
      }
    });
  }
}
