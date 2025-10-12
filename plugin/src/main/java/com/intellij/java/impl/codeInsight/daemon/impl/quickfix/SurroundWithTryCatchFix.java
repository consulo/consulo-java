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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import com.intellij.java.language.psi.*;
import com.intellij.java.impl.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.ScrollType;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

/**
 * @author mike
 * Date: Aug 19, 2002
 */
public class SurroundWithTryCatchFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(SurroundWithTryCatchFix.class);

  private PsiStatement myStatement = null;

  public SurroundWithTryCatchFix(PsiElement element) {
    final PsiMethodReferenceExpression methodReferenceExpression = PsiTreeUtil.getParentOfType(element, PsiMethodReferenceExpression.class, false);
    if (methodReferenceExpression == null) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression == null || lambdaExpression.getBody() instanceof PsiCodeBlock) {
        myStatement = PsiTreeUtil.getNonStrictParentOfType(element, PsiStatement.class);
      }
    }
  }

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return JavaQuickFixLocalize.surroundWithTryCatchFix();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myStatement != null &&
           myStatement.isValid() &&
           (!(myStatement instanceof PsiExpressionStatement) ||
            !RefactoringChangeUtil.isSuperOrThisMethodCall(((PsiExpressionStatement)myStatement).getExpression()));
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    int col = editor.getCaretModel().getLogicalPosition().column;
    int line = editor.getCaretModel().getLogicalPosition().line;
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 0));

    if (myStatement.getParent() instanceof PsiForStatement) {
      PsiForStatement forStatement = (PsiForStatement)myStatement.getParent();
      if (myStatement.equals(forStatement.getInitialization()) || myStatement.equals(forStatement.getUpdate())) {
        myStatement = forStatement;
      }
    }
    TextRange range = null;

    try{
      JavaWithTryCatchSurrounder handler = new JavaWithTryCatchSurrounder();
      range = handler.surroundElements(project, editor, new PsiElement[] {myStatement});
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    LogicalPosition pos = new LogicalPosition(line, col);
    editor.getCaretModel().moveToLogicalPosition(pos);
    if (range != null) {
      int offset = range.getStartOffset();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
