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

import com.intellij.java.language.psi.PsiBinaryExpression;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.ElementPattern;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
public class PsiBinaryExpressionPattern extends PsiExpressionPattern<PsiBinaryExpression, PsiBinaryExpressionPattern> {
  protected PsiBinaryExpressionPattern() {
    super(PsiBinaryExpression.class);
  }

  public PsiBinaryExpressionPattern left(@Nonnull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("left") {
      public boolean accepts(@Nonnull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.getCondition().accepts(psiBinaryExpression.getLOperand(), context);
      }
    });
  }

  public PsiBinaryExpressionPattern right(@Nonnull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("right") {
      public boolean accepts(@Nonnull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.getCondition().accepts(psiBinaryExpression.getROperand(), context);
      }
    });
  }

  public PsiBinaryExpressionPattern operation(final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("operation") {
      public boolean accepts(@Nonnull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.getCondition().accepts(psiBinaryExpression.getOperationSign(), context);
      }
    });
  }

}
