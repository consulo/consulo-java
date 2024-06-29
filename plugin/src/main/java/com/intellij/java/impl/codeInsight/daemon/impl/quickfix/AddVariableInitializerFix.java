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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class AddVariableInitializerFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiVariable myVariable;

  public AddVariableInitializerFix(PsiVariable variable) {
    myVariable = variable;
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public String getText() {
    return CodeInsightLocalize.quickfixAddVariableText(myVariable.getName()).get();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myVariable != null
      && myVariable.isValid()
      && myVariable.getManager().isInProject(myVariable)
      && !myVariable.hasInitializer()
      && !(myVariable instanceof PsiParameter);
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) return;

    String initializerText = suggestInitializer();
    PsiElementFactory factory = JavaPsiFacade.getInstance(myVariable.getProject()).getElementFactory();
    PsiExpression initializer = factory.createExpressionFromText(initializerText, myVariable);
    if (myVariable instanceof PsiLocalVariable localVariable) {
      localVariable.setInitializer(initializer);
    }
    else if (myVariable instanceof PsiField field) {
      field.setInitializer(initializer);
    }
    else {
      LOG.error("Unknown variable type: " + myVariable);
    }
    PsiVariable var = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myVariable);
    TextRange range = var.getInitializer().getTextRange();
    int offset = range.getStartOffset();
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
  }

  private String suggestInitializer() {
    PsiType type = myVariable.getType();
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
