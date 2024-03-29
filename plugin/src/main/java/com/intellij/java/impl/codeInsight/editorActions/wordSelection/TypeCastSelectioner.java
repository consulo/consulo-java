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
package com.intellij.java.impl.codeInsight.editorActions.wordSelection;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.document.util.TextRange;
import consulo.codeEditor.Editor;

import java.util.List;
import java.util.ArrayList;

@ExtensionImpl
public class TypeCastSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiTypeCastExpression;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();
    result.addAll(expandToWholeLine(editorText, e.getTextRange(), false));

    PsiTypeCastExpression expression = (PsiTypeCastExpression)e;
    PsiElement[] children = expression.getChildren();
    PsiElement lParen = null;
    PsiElement rParen = null;
    for (PsiElement child : children) {
      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;
        if (token.getTokenType() == JavaTokenType.LPARENTH) lParen = token;
        if (token.getTokenType() == JavaTokenType.RPARENTH) rParen = token;
      }
    }

    if (lParen != null && rParen != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(lParen.getTextRange().getStartOffset(),
                                                    rParen.getTextRange().getEndOffset()),
                                      false));
    }

    return result;
  }
}
