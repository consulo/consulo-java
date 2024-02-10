/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.bool;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.siyeh.IntentionPowerPackBundle;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.FlipConjunctionIntention", fileExtensions = "java", categories = {
		"Java",
		"Boolean"
})
public class FlipConjunctionIntention extends MutablyNamedIntention {

  protected String getTextForElement(PsiElement element) {
    final PsiPolyadicExpression binaryExpression =
      (PsiPolyadicExpression)element;
    PsiExpression op = binaryExpression.getOperands()[1];
    final PsiJavaToken sign = binaryExpression.getTokenBeforeOperand(op);
    return IntentionPowerPackBundle.message("flip.smth.intention.name",
                                            sign.getText());
  }

  @Nonnull
  public PsiElementPredicate getElementPredicate() {
    return new ConjunctionPredicate();
  }

  public void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    PsiExpression exp = (PsiExpression)element;
    final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)exp;
    final IElementType conjunctionType = binaryExpression.getOperationTokenType();
    PsiElement parent = exp.getParent();
    while (isConjunctionExpression(parent, conjunctionType)) {
      exp = (PsiExpression)parent;
      assert exp != null;
      parent = exp.getParent();
    }
    final String newExpression = flipExpression(exp, conjunctionType);
    replaceExpression(newExpression, exp);
  }

  private static String flipExpression(PsiExpression expression,
                                       IElementType conjunctionType) {
    if (!isConjunctionExpression(expression, conjunctionType)) {
      return expression.getText();
    }
    final PsiPolyadicExpression andExpression =
      (PsiPolyadicExpression)expression;
    final String conjunctionSign;
    if (conjunctionType.equals(JavaTokenType.ANDAND)) {
      conjunctionSign = "&&";
    }
    else {
      conjunctionSign = "||";
    }
    String r = null;
    PsiExpression[] operands = andExpression.getOperands();
    for (int i = operands.length - 1; i >= 0; i--) {
      PsiExpression op = operands[i];
      String flip = flipExpression(op, conjunctionType);
      r = r == null ? flip : r + ' ' + conjunctionSign + ' ' + flip;
    }
    return r;
  }

  private static boolean isConjunctionExpression(
    PsiElement element, IElementType conjunctionType) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression binaryExpression =
      (PsiPolyadicExpression)element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    return tokenType.equals(conjunctionType);
  }
}
