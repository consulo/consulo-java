/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.braces;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiArrayInitializerExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiType;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

public class AddArrayCreationExpressionIntention extends MutablyNamedIntention {

  @Override
  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new ArrayCreationExpressionPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiArrayInitializerExpression arrayInitializerExpression =
      (PsiArrayInitializerExpression)element;
    final PsiType type = arrayInitializerExpression.getType();
    assert type != null;
    return IntentionPowerPackBundle.message(
      "add.array.creation.expression.intention.name",
      type.getPresentableText());
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiArrayInitializerExpression arrayInitializerExpression =
      (PsiArrayInitializerExpression)element;
    final PsiType type = arrayInitializerExpression.getType();
    if (type == null) {
      return;
    }
    final String typeText = type.getCanonicalText();
    final String newExpressionText =
      "new " + typeText + arrayInitializerExpression.getText();
    replaceExpression(newExpressionText, arrayInitializerExpression);
  }
}
