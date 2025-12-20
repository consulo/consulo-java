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

import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.RemoveRedundantElseAction", categories = {"Java", "Control Flow"}, fileExtensions = "java")
public class RemoveRedundantElseAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(RemoveRedundantElseAction.class);

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return JavaQuickFixLocalize.removeRedundantElseFix();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (element instanceof PsiKeyword &&
        element.getParent() instanceof PsiIfStatement &&
        PsiKeyword.ELSE.equals(element.getText())) {
      PsiIfStatement ifStatement = (PsiIfStatement) element.getParent();
      if (ifStatement.getElseBranch() == null) return false;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) return false;
      PsiElement block = PsiTreeUtil.getParentOfType(ifStatement, PsiCodeBlock.class);
      if (block != null) {
        while (cantCompleteNormally(thenBranch, block)) {
          thenBranch = getPrevThenBranch(thenBranch);
          if (thenBranch == null) return true;
        }
        return false;
      }
    }
    return false;
  }

  @Nullable
  private static PsiStatement getPrevThenBranch(@Nonnull PsiElement thenBranch) {
    PsiElement ifStatement = thenBranch.getParent();
    PsiElement parent = ifStatement.getParent();
    if (parent instanceof PsiIfStatement && ((PsiIfStatement) parent).getElseBranch() == ifStatement) {
      return ((PsiIfStatement) parent).getThenBranch();
    }
    return null;
  }

  private static boolean cantCompleteNormally(@Nonnull PsiStatement thenBranch, PsiElement block) {
    try {
      ControlFlow controlFlow = ControlFlowFactory.getInstance(thenBranch.getProject()).getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
      int startOffset = controlFlow.getStartOffset(thenBranch);
      int endOffset = controlFlow.getEndOffset(thenBranch);
      return startOffset != -1 && endOffset != -1 && !ControlFlowUtil.canCompleteNormally(controlFlow, startOffset, endOffset);
    } catch (AnalysisCanceledException e) {
      return false;
    }
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    PsiIfStatement ifStatement = (PsiIfStatement) element.getParent();
    LOG.assertTrue(ifStatement != null && ifStatement.getElseBranch() != null);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch instanceof PsiBlockStatement) {
      PsiElement[] statements = ((PsiBlockStatement) elseBranch).getCodeBlock().getStatements();
      if (statements.length > 0) {
        ifStatement.getParent().addRangeAfter(statements[0], statements[statements.length - 1], ifStatement);
      }
    } else {
      ifStatement.getParent().addAfter(elseBranch, ifStatement);
    }
    ifStatement.getElseBranch().delete();
  }
}
