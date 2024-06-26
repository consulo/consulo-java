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

import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.CharArrayUtil;
import consulo.xml.psi.xml.XmlChildRole;
import consulo.xml.psi.xml.XmlTag;

/**
 * @author maxim
 */
public class XmlTagFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof XmlTag) {
      final ASTNode emptyTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(psiElement.getNode());
      final ASTNode endTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(psiElement.getNode());
      if (emptyTagEnd != null || endTagEnd != null) {
        return;
      }

      int insertionOffset = psiElement.getTextRange().getEndOffset();
      Document doc = editor.getDocument();
      final int caretAt = editor.getCaretModel().getOffset();
      final CharSequence text = doc.getCharsSequence();
      final int probableCommaOffset = CharArrayUtil.shiftForward(text, insertionOffset, " \t");

      //if (caretAt < probableCommaOffset) {
      //  final CharSequence tagName = text.subSequence(psiElement.getTextRange().getStartOffset() + 1, caretAt);
      //  doc.insertString(caretAt, ">");
      //  doc.insertString(probableCommaOffset + 1, "</" +  tagName + ">");
      //  return;
      //}

      char ch;
      if (probableCommaOffset >= text.length() || ((ch = text.charAt(probableCommaOffset)) != '/' && ch != '>')) {
        doc.insertString(insertionOffset, "/>");
      }
    }
  }
}