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
package com.intellij.java.impl.codeInsight.unwrap;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Danila Ponomarenko
 */
public class JavaPolyadicExpressionUnwrapper extends JavaUnwrapper {
  public JavaPolyadicExpressionUnwrapper() {
    super("");
  }

  @Override
  public String getDescription(PsiElement e) {
    return CodeInsightLocalize.unwrapWithPlaceholder(e.getText()).get();
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    if (!(e.getParent() instanceof PsiPolyadicExpression)) {
      return false;
    }

    final PsiPolyadicExpression expression = (PsiPolyadicExpression)e.getParent();

    final PsiExpression operand = findOperand(e, expression);

    return operand != null;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    final PsiPolyadicExpression parent = (PsiPolyadicExpression)element.getParent();

    final PsiExpression operand = findOperand(element, parent);

    if (operand == null) {
      return;
    }

    context.extractElement(operand, parent);
    context.delete(parent);
  }

  @Nullable
  @RequiredReadAction
  private static PsiExpression findOperand(@Nonnull PsiElement e, @Nonnull PsiPolyadicExpression expression) {
    final TextRange elementTextRange = e.getTextRange();

    for (PsiExpression operand : expression.getOperands()) {
      final TextRange operandTextRange = operand.getTextRange();
      if (operandTextRange != null && operandTextRange.contains(elementTextRange)) {
        return operand;
      }
    }
    return null;
  }
}
