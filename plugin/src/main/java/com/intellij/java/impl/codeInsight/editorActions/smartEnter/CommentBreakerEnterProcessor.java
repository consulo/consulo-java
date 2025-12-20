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
package com.intellij.java.impl.codeInsight.editorActions.smartEnter;

import com.intellij.java.language.psi.JavaTokenType;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.util.EditorModificationUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.ex.action.IdeActions;

/**
 * User: max
 * Date: Sep 8, 2003
 * Time: 2:57:38 PM
 */
public class CommentBreakerEnterProcessor implements EnterProcessor {
  @Override
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    if (isModified) {
      return false;
    }
    PsiElement atCaret = psiElement.getContainingFile().findElementAt(editor.getCaretModel().getOffset());
    PsiComment comment = PsiTreeUtil.getParentOfType(atCaret, PsiComment.class, false);
    if (comment != null) {
      plainEnter(editor);
      if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        EditorModificationUtil.insertStringAtCaret(editor, "// ");
      }
      return true;
    }
    return false;
  }

  private static void plainEnter(Editor editor) {
    getEnterHandler().execute(editor, ((EditorEx) editor).getDataContext());
  }

  private static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

}
