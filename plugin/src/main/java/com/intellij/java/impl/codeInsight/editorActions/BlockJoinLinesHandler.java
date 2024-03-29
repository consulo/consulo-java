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
package com.intellij.java.impl.codeInsight.editorActions;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.action.JoinLinesHandlerDelegate;
import com.intellij.java.language.psi.*;
import consulo.document.Document;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

@ExtensionImpl
public class BlockJoinLinesHandler implements JoinLinesHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(BlockJoinLinesHandler.class);

  @Override
  public int tryJoinLines(final Document document, final PsiFile psiFile, final int start, final int end) {
    PsiElement elementAtStartLineEnd = psiFile.findElementAt(start);
    PsiElement elementAtNextLineStart = psiFile.findElementAt(end);
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;
    if (!(elementAtStartLineEnd instanceof PsiJavaToken) || ((PsiJavaToken) elementAtStartLineEnd).getTokenType() != JavaTokenType.LBRACE) {
      return -1;
    }
    final PsiElement codeBlock = elementAtStartLineEnd.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return -1;
    if (!(codeBlock.getParent() instanceof PsiBlockStatement)) return -1;
    final PsiElement parentStatement = codeBlock.getParent().getParent();

    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(elementAtStartLineEnd.getProject());
    if (!(parentStatement instanceof PsiIfStatement && codeStyleSettings.IF_BRACE_FORCE != CommonCodeStyleSettings.FORCE_BRACES_ALWAYS ||
        parentStatement instanceof PsiWhileStatement && codeStyleSettings.WHILE_BRACE_FORCE !=
            CommonCodeStyleSettings.FORCE_BRACES_ALWAYS ||
        (parentStatement instanceof PsiForStatement || parentStatement instanceof PsiForeachStatement) &&
            codeStyleSettings.FOR_BRACE_FORCE != CommonCodeStyleSettings.FORCE_BRACES_ALWAYS ||
        parentStatement instanceof PsiDoWhileStatement &&
            codeStyleSettings
                .DOWHILE_BRACE_FORCE !=
                CommonCodeStyleSettings.FORCE_BRACES_ALWAYS)) {
      return -1;
    }
    PsiElement foundStatement = null;
    for (PsiElement element = elementAtStartLineEnd.getNextSibling(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiWhiteSpace) continue;
      if (element instanceof PsiJavaToken &&
          ((PsiJavaToken) element).getTokenType() == JavaTokenType.RBRACE &&
          element.getParent() == codeBlock) {
        if (foundStatement == null) return -1;
        break;
      }
      if (foundStatement != null) return -1;
      foundStatement = element;
    }
    try {
      final PsiElement newStatement = codeBlock.getParent().replace(foundStatement);

      return newStatement.getTextRange().getStartOffset();
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return -1;
  }
}
