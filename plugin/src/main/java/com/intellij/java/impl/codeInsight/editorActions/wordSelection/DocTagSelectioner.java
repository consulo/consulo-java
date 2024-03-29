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
import consulo.ide.impl.idea.codeInsight.editorActions.wordSelection.WordSelectioner;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.javadoc.PsiDocToken;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.ast.IElementType;
import consulo.util.lang.CharArrayUtil;
import consulo.annotation.access.RequiredReadAction;

import java.util.List;

@ExtensionImpl
public class DocTagSelectioner extends WordSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiDocTag;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    result.add(getDocTagRange((PsiDocTag) e, editorText, cursorOffset));
    return result;
  }

  @RequiredReadAction
  public static TextRange getDocTagRange(PsiDocTag e, CharSequence documentText, int minOffset) {
    TextRange range = e.getTextRange();

    int endOffset = range.getEndOffset();
    int startOffset = range.getStartOffset();

    PsiElement[] children = e.getChildren();

    for (int i = children.length - 1; i >= 0; i--) {
      PsiElement child = children[i];

      int childStartOffset = child.getTextRange().getStartOffset();

      if (childStartOffset <= minOffset) {
        break;
      }

      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken) child;

        IElementType type = token.getTokenType();
        char[] chars = token.textToCharArray();
        int shift = CharArrayUtil.shiftForward(chars, 0, " \t\n\r");

        if (shift != chars.length && type != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
          break;
        }
      } else if (!(child instanceof PsiWhiteSpace)) {
        break;
      }

      endOffset = Math.min(childStartOffset, endOffset);
    }

    startOffset = CharArrayUtil.shiftBackward(documentText, startOffset - 1, "* \t") + 1;

    return new TextRange(startOffset, endOffset);
  }
}
