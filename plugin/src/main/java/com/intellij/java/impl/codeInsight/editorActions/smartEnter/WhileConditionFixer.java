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

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiWhileStatement;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 5:32:01 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class WhileConditionFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiWhileStatement) {
      final Document doc = editor.getDocument();
      final PsiWhileStatement whileStatement = (PsiWhileStatement) psiElement;
      final PsiJavaToken rParenth = whileStatement.getRParenth();
      final PsiJavaToken lParenth = whileStatement.getLParenth();
      final PsiExpression condition = whileStatement.getCondition();

      if (condition == null) {
        if (lParenth == null || rParenth == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(whileStatement.getTextRange().getStartOffset()));
          final PsiStatement block = whileStatement.getBody();
          if (block != null) {
            stopOffset = Math.min(stopOffset, block.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, whileStatement.getTextRange().getEndOffset());

          doc.replaceString(whileStatement.getTextRange().getStartOffset(), stopOffset, "while ()");
          processor.registerUnresolvedError(whileStatement.getTextRange().getStartOffset() + "while (".length());
        } else {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
      } else if (rParenth == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
