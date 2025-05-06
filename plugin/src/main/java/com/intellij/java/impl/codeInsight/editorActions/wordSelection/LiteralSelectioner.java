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
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.action.SelectWordUtil;
import consulo.language.lexer.StringLiteralLexer;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiLiteralExpression;

import java.util.List;

@ExtensionImpl
public class LiteralSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    PsiElement parent = e.getParent();
    return
      isStringLiteral(e) || isStringLiteral(parent);
  }

  private static boolean isStringLiteral(PsiElement element) {
    return element instanceof PsiLiteralExpression &&
           ((PsiLiteralExpression)element).getType().equalsToText(JavaClassNames.JAVA_LANG_STRING) && element.getText().startsWith("\"") && element.getText().endsWith("\"");
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    TextRange range = e.getTextRange();
    SelectWordUtil.addWordHonoringEscapeSequences(editorText, range, cursorOffset,
                                                  new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL),
                                                  result);

    result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));

    return result;
  }
}
