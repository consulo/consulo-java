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

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiStatement;
import consulo.document.util.TextRange;
import consulo.codeEditor.Editor;

import java.util.List;
import java.util.ArrayList;

public class IfStatementSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiIfStatement;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();
    result.addAll(expandToWholeLine(editorText, e.getTextRange(), false));

    PsiIfStatement statement = (PsiIfStatement)e;

    final PsiKeyword elseKeyword = statement.getElseElement();
    if (elseKeyword != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                    statement.getTextRange().getEndOffset()),
                                      false));

      final PsiStatement branch = statement.getElseBranch();
      if (branch instanceof PsiIfStatement) {
        PsiIfStatement elseIf = (PsiIfStatement)branch;
        final PsiKeyword element = elseIf.getElseElement();
        if (element != null) {
          result.addAll(expandToWholeLine(editorText,
                                          new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                        elseIf.getThenBranch().getTextRange().getEndOffset()),
                                          false));
        }
      }
    }

    return result;
  }
}
