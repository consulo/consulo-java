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
import consulo.ide.impl.idea.codeInsight.editorActions.wordSelection.LineCommentSelectioner;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocToken;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.util.lang.CharArrayUtil;

import java.util.List;

@ExtensionImpl
public class DocCommentSelectioner extends LineCommentSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiDocComment;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    PsiElement[] children = e.getChildren();

    int startOffset = e.getTextRange().getStartOffset();
    int endOffset = e.getTextRange().getEndOffset();

    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken) child;

        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          char[] chars = token.getText().toCharArray();

          if (CharArrayUtil.shiftForward(chars, 0, " *\n\t\r") != chars.length) {
            break;
          }
        }
      }

      startOffset = child.getTextRange().getEndOffset();
    }

    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken) child;

        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          char[] chars = token.getText().toCharArray();

          if (CharArrayUtil.shiftForward(chars, 0, " *\n\t\r") != chars.length) {
            endOffset = child.getTextRange().getEndOffset();
          }
        }
      }
    }

    startOffset = CharArrayUtil.shiftBackward(editorText, startOffset - 1, "* \t") + 1;

    result.add(new TextRange(startOffset, endOffset));

    return result;
  }
}
