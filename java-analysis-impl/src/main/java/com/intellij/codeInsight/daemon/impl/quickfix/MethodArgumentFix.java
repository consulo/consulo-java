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

import consulo.java.JavaQuickFixBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public abstract class MethodArgumentFix implements IntentionAction {
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


  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("fix.argument.family");
  }
}
