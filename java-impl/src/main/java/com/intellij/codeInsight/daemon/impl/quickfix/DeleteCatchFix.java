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
package com.intellij.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiTryStatement;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class DeleteCatchFix implements IntentionAction {
  private final PsiParameter myCatchParameter;

  public DeleteCatchFix(@Nonnull PsiParameter myCatchParameter) {
    this.myCatchParameter = myCatchParameter;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("delete.catch.text", JavaHighlightUtil.formatType(myCatchParameter.getType()));
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("delete.catch.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myCatchParameter.isValid() && PsiManager.getInstance(project).isInProject(myCatchParameter.getContainingFile());
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myCatchParameter.getContainingFile())) return;

    final PsiTryStatement tryStatement = ((PsiCatchSection)myCatchParameter.getDeclarationScope()).getTryStatement();
    if (tryStatement.getCatchBlocks().length == 1 && tryStatement.getFinallyBlock() == null) {
      // unwrap entire try statement
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      PsiElement lastAddedStatement = null;
      if (tryBlock != null) {
        final PsiElement firstElement = tryBlock.getFirstBodyElement();
        if (firstElement != null) {
          final PsiElement tryParent = tryStatement.getParent();
          if (tryParent instanceof PsiCodeBlock) {
            final PsiElement lastBodyElement = tryBlock.getLastBodyElement();
            assert lastBodyElement != null : tryBlock.getText();
            tryParent.addRangeBefore(firstElement, lastBodyElement, tryStatement);
            lastAddedStatement = tryStatement.getPrevSibling();
            while (lastAddedStatement != null && (lastAddedStatement instanceof PsiWhiteSpace || lastAddedStatement.getTextLength() == 0)) {
              lastAddedStatement = lastAddedStatement.getPrevSibling();
            }
          }
          else {
            tryParent.addBefore(tryBlock, tryStatement);
            lastAddedStatement = tryBlock;
          }
        }
      }
      tryStatement.delete();
      if (lastAddedStatement != null) {
        editor.getCaretModel().moveToOffset(lastAddedStatement.getTextRange().getEndOffset());
      }

      return;
    }

    // delete catch section
    final PsiElement catchSection = myCatchParameter.getParent();
    assert catchSection instanceof PsiCatchSection : catchSection;
    //save previous element to move caret to
    PsiElement previousElement = catchSection.getPrevSibling();
    while (previousElement instanceof PsiWhiteSpace) {
      previousElement = previousElement.getPrevSibling();
    }
    catchSection.delete();
    if (previousElement != null) {
      //move caret to previous catch section
      editor.getCaretModel().moveToOffset(previousElement.getTextRange().getEndOffset());
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
