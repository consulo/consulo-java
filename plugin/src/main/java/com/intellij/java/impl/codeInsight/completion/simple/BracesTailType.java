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

package com.intellij.java.impl.codeInsight.completion.simple;

import consulo.language.editor.completion.lookup.TailType;
import com.intellij.codeInsight.editorActions.EnterHandler;
import consulo.ide.impl.idea.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import consulo.dataContext.DataManager;
import consulo.ui.ex.action.IdeActions;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionManager;
import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.util.lang.CharArrayUtil;

/**
 * @author peter
 */
public class BracesTailType extends TailType {

  @Override
  public int processTail(final Editor editor, int tailOffset) {
    int startOffset = tailOffset;

    CharSequence seq = editor.getDocument().getCharsSequence();
    int nextNonWs = CharArrayUtil.shiftForward(seq, tailOffset, " \t");
    if (nextNonWs < seq.length() && seq.charAt(nextNonWs) == '{') {
      tailOffset = nextNonWs + 1;
    } else {
      tailOffset = insertChar(editor, startOffset, '{');
    }

    tailOffset = reformatBrace(editor, tailOffset, startOffset);

    if (EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, tailOffset, getFileType(editor))) {
      new EnterHandler(EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER))
        .executeWriteAction(editor, DataManager.getInstance().getDataContext(editor.getContentComponent()));
      return editor.getCaretModel().getOffset();
    }
    return tailOffset;
  }

  private static int reformatBrace(Editor editor, int tailOffset, int startOffset) {
    Project project = editor.getProject();
    if (project != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        editor.getCaretModel().moveToOffset(tailOffset);
        CodeStyleManager.getInstance(project).reformatText(psiFile, startOffset, tailOffset);
        tailOffset = editor.getCaretModel().getOffset();
      }
    }
    return tailOffset;
  }
}