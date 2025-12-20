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

import com.intellij.java.impl.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.util.TextRange;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class AddExceptionToCatchFix extends BaseIntentionAction implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(AddExceptionToCatchFix.class);

  public AddExceptionToCatchFix() {
    setText(JavaQuickFixLocalize.addCatchClauseFamily());
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    int offset = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element = findElement(file, offset);
    PsiTryStatement tryStatement = (PsiTryStatement) element.getParent();
    List<PsiClassType> unhandledExceptions = new ArrayList<PsiClassType>(ExceptionUtil.collectUnhandledExceptions(element, null));
    ExceptionUtil.sortExceptionsByHierarchy(unhandledExceptions);

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    PsiCodeBlock catchBlockToSelect = null;

    try {
      if (tryStatement.getFinallyBlock() == null && tryStatement.getCatchBlocks().length == 0) {
        for (PsiClassType unhandledException : unhandledExceptions) {
          addCatchStatement(tryStatement, unhandledException, file);
        }
        catchBlockToSelect = tryStatement.getCatchBlocks()[0];
      }
      else {
        for (PsiClassType unhandledException : unhandledExceptions) {
          PsiCodeBlock codeBlock = addCatchStatement(tryStatement, unhandledException, file);
          if (catchBlockToSelect == null) catchBlockToSelect = codeBlock;
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    if (catchBlockToSelect != null) {
      TextRange range = SurroundWithUtil.getRangeToSelect(catchBlockToSelect);
      editor.getCaretModel().moveToOffset(range.getStartOffset());

      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  private static PsiCodeBlock addCatchStatement(PsiTryStatement tryStatement, PsiClassType exceptionType, PsiFile file) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(tryStatement.getProject()).getElementFactory();

    if (tryStatement.getTryBlock() == null) {
      addTryBlock(tryStatement, factory);
    }

    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(tryStatement.getProject());
    String name = styleManager.suggestVariableName(VariableKind.PARAMETER, null, null, exceptionType).names[0];
    name = styleManager.suggestUniqueVariableName(name, tryStatement, false);

    PsiCatchSection catchSection = factory.createCatchSection(exceptionType, name, file);

    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock == null) {
      tryStatement.add(catchSection);
    }
    else {
      tryStatement.addBefore(catchSection, getFinallySectionStart(finallyBlock));
    }

    PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
    parameters[parameters.length - 1].getTypeElement().replace(factory.createTypeElement(exceptionType));
    PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();

    return catchBlocks[catchBlocks.length - 1];
  }

  private static void addTryBlock(PsiTryStatement tryStatement, PsiElementFactory factory) {
    PsiCodeBlock tryBlock = factory.createCodeBlock();

    PsiElement anchor;
    PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    if (catchSections.length > 0) {
      anchor = catchSections[0];
    }
    else {
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      anchor = finallyBlock != null ? getFinallySectionStart(finallyBlock) : null;
    }

    if (anchor != null) {
      tryStatement.addBefore(tryBlock, anchor);
    }
    else {
      tryStatement.add(tryBlock);
    }
  }

  private static PsiElement getFinallySectionStart(@Nonnull PsiCodeBlock finallyBlock) {
    PsiElement finallyElement = finallyBlock;
    while (!PsiUtil.isJavaToken(finallyElement, JavaTokenType.FINALLY_KEYWORD) && finallyElement != null) {
      finallyElement = finallyElement.getPrevSibling();
    }
    assert finallyElement != null : finallyBlock.getParent().getText();
    return finallyElement;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = findElement(file, offset);

    if (element == null) return false;

    setText(JavaQuickFixLocalize.addCatchClauseText());
    return true;
  }

  @Nullable
  private static PsiElement findElement(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) element = file.findElementAt(offset - 1);
    if (element == null) return null;

    @SuppressWarnings({"unchecked"}) PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class, PsiMethod.class);
    if (parent == null || parent instanceof PsiMethod) return null;
    PsiTryStatement statement = (PsiTryStatement) parent;

    PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null && tryBlock.getTextRange().contains(offset)) {
      if (!ExceptionUtil.collectUnhandledExceptions(tryBlock, statement.getParent()).isEmpty()) {
        return tryBlock;
      }
    }

    PsiResourceList resourceList = statement.getResourceList();
    if (resourceList != null && resourceList.getTextRange().contains(offset)) {
      if (!ExceptionUtil.collectUnhandledExceptions(resourceList, statement.getParent()).isEmpty()) {
        return resourceList;
      }
    }

    return null;
  }
}
