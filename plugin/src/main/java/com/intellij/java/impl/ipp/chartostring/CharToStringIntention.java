/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.chartostring;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.CharToStringIntention", fileExtensions = "java", categories = {"Java", "Strings"})
public class CharToStringIntention extends Intention {

  @Override
  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new CharToStringPredicate();
  }

  @Override
  public void processIntention(@jakarta.annotation.Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiLiteralExpression charLiteral =
      (PsiLiteralExpression)element;
    final String charLiteralText = charLiteral.getText();
    final String stringLiteral = stringForCharLiteral(charLiteralText);
    replaceExpression(stringLiteral, charLiteral);
  }

  private static String stringForCharLiteral(String charLiteral) {
    if ("'\"'".equals(charLiteral)) {
      return "\"\\\"\"";
    }
    else if ("'\\''".equals(charLiteral)) {
      return "\"'\"";
    }
    else {
      return '\"' + charLiteral.substring(1, charLiteral.length() - 1) +
             '\"';
    }
  }
}