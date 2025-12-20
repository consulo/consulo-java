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

import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiSwitchLabelStatement;
import com.intellij.java.language.psi.PsiSwitchStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.*;
import consulo.document.util.TextRange;
import consulo.codeEditor.Editor;
import consulo.document.Document;

import java.util.List;
import java.util.ArrayList;

@ExtensionImpl
public class CaseStatementsSelectioner extends BasicSelectioner {
    @Override
    public boolean canSelect(PsiElement e) {
      return  e.getParent() instanceof PsiCodeBlock &&
             e.getParent().getParent() instanceof PsiSwitchStatement;
    }

    @Override
    public List<TextRange> select(PsiElement statement, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = new ArrayList<TextRange>();
      PsiElement caseStart = statement;
      PsiElement caseEnd = statement;

      if (statement instanceof PsiSwitchLabelStatement ||
          statement instanceof PsiSwitchStatement) {
        return result;
      }

      PsiElement sibling = statement.getPrevSibling();
      while(sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
        if (!(sibling instanceof PsiWhiteSpace)) caseStart = sibling;
        sibling = sibling.getPrevSibling();
      }

      sibling = statement.getNextSibling();
      while(sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
        if (!(sibling instanceof PsiWhiteSpace) &&
            !(sibling instanceof PsiJavaToken) // end of switch
           ) {
          caseEnd = sibling;
        }
        sibling = sibling.getNextSibling();
      }

      Document document = editor.getDocument();
      int startOffset = document.getLineStartOffset(document.getLineNumber(caseStart.getTextOffset()));
      int endOffset = document.getLineEndOffset(document.getLineNumber(caseEnd.getTextOffset() + caseEnd.getTextLength())) + 1;

      result.add(new TextRange(startOffset,endOffset));
      return result;
    }
  }
