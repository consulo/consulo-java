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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import consulo.language.editor.action.ExtendWordSelectionHandler;
import consulo.language.Language;
import com.intellij.java.language.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

public class AntLikePropertySelectionHandler implements ExtendWordSelectionHandler {
  @Override
  public boolean canSelect(PsiElement e) {
    Language l = e.getLanguage();
    if (!(l.equals(JavaLanguage.INSTANCE)
          || l.equals(XMLLanguage.INSTANCE))) {
      return false;
    }

    return PsiTreeUtil.getParentOfType(e, PsiComment.class) == null;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    TextRange range = e.getTextRange();
    char prevLeftChar = ' ';
    for (int left = cursorOffset; left >= range.getStartOffset(); left--) {
      char leftChar = editorText.charAt(left);
      if (leftChar == '}') return Collections.emptyList();
      if (leftChar == '$' && prevLeftChar == '{') {
        for (int right = cursorOffset; right < range.getEndOffset(); right++) {
          char rightChar = editorText.charAt(right);
          if (rightChar == '{') return Collections.emptyList();
          if (rightChar == '}') {
            return Arrays.asList(new TextRange(left + 2, right), new TextRange(left, right + 1));
          }
        }
      }
      prevLeftChar = leftChar;
    }
    return Collections.emptyList();
  }
}
