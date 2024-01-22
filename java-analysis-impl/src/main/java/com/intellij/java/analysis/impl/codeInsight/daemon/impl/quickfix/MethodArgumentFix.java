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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.PsiEllipsisType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public abstract class MethodArgumentFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(MethodArgumentFix.class);

  protected final PsiExpressionList myArgList;
  protected final int myIndex;
  private final ArgumentFixerActionFactory myArgumentFixerActionFactory;
  protected final PsiType myToType;

  protected MethodArgumentFix(PsiExpressionList list, int i, PsiType toType, ArgumentFixerActionFactory fixerActionFactory) {
    myArgList = list;
    myIndex = i;
    myArgumentFixerActionFactory = fixerActionFactory;
    myToType = toType instanceof PsiEllipsisType ? ((PsiEllipsisType) toType).toArrayType() : toType;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return
        myToType != null
        && myToType.isValid()
        && myArgList != null
        && myArgList.getExpressions().length > myIndex
        && myArgList.getExpressions()[myIndex] != null
        && myArgList.getExpressions()[myIndex].isValid();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PsiExpression expression = myArgList.getExpressions()[myIndex];

    try {
      LOG.assertTrue(expression != null && expression.isValid());
      PsiExpression modified = myArgumentFixerActionFactory.getModifiedArgument(expression, myToType);
      LOG.assertTrue(modified != null, myArgumentFixerActionFactory);
      expression.replace(modified);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
