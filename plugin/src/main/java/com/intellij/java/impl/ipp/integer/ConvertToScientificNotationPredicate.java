/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.integer;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

/**
 * @author Konstantin Bulenkov
 */
class ConvertToScientificNotationPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression expression = (PsiLiteralExpression)element;
    final PsiType type = expression.getType();
    if (!PsiType.DOUBLE.equals(type) && !PsiType.FLOAT.equals(type)) {
      return false;
    }
    String text = expression.getText();
    if (text == null) {
      return false;
    }
    text = text.toLowerCase();
    if (text.startsWith("-")) {
      text = text.substring(1);
    }
    if (!text.contains("") && text.startsWith("0")) {
      return false; //Octal integer
    }
    return !text.contains("e");
  }
}
