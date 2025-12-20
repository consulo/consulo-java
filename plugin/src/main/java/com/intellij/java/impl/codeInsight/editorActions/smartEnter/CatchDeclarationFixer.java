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

import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiJavaToken;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

/**
 * User: max
 * Date: Sep 5, 2003
 * Time: 5:32:01 PM
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class CatchDeclarationFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiCatchSection) {
      Document doc = editor.getDocument();
      PsiCatchSection catchSection = (PsiCatchSection) psiElement;

      int catchStart = catchSection.getTextRange().getStartOffset();
      int stopOffset = doc.getLineEndOffset(doc.getLineNumber(catchStart));

      PsiCodeBlock catchBlock = catchSection.getCatchBlock();
      if (catchBlock != null) {
        stopOffset = Math.min(stopOffset, catchBlock.getTextRange().getStartOffset());
      }
      stopOffset = Math.min(stopOffset, catchSection.getTextRange().getEndOffset());

      PsiJavaToken lParenth = catchSection.getLParenth();
      if (lParenth == null) {
        doc.replaceString(catchStart, stopOffset, "catch ()");
        processor.registerUnresolvedError(catchStart + "catch (".length());
      } else {
        if (catchSection.getParameter() == null) {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
        if (catchSection.getRParenth() == null) {
          doc.insertString(stopOffset, ")");
        }
      }
    }
  }
}