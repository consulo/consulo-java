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
package com.intellij.java.impl.codeInsight.editorActions.wordSelection;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.action.AbstractWordSelectioner;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.ast.IElementType;

import java.util.Iterator;
import java.util.List;

/**
 * @author peter
 */
@ExtensionImpl
public class JavaWordSelectioner extends AbstractWordSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    if (e instanceof PsiKeyword) {
      return true;
    }
    if (e instanceof PsiJavaToken) {
      IElementType tokenType = ((PsiJavaToken)e).getTokenType();
      return tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.STRING_LITERAL;
    }
    return false;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> ranges = super.select(e, editorText, cursorOffset, editor);
    if (e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType() == JavaTokenType.STRING_LITERAL) {
      killRangesBreakingEscapes(e, ranges, e.getTextRange());
    }
    return ranges;
  }

  private static void killRangesBreakingEscapes(PsiElement e, List<TextRange> ranges, TextRange literalRange) {
    for (Iterator<TextRange> iterator = ranges.iterator(); iterator.hasNext(); ) {
      TextRange each = iterator.next();
      if (literalRange.contains(each) &&
          literalRange.getStartOffset() < each.getStartOffset() &&
          e.getText().charAt(each.getStartOffset() - literalRange.getStartOffset() - 1) == '\\') {
        iterator.remove();
      }
    }
  }
}
