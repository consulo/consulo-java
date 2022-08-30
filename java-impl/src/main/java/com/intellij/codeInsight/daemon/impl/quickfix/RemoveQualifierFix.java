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
import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.util.IncorrectOperationException;

/**
 * @author cdr
 */
public class RemoveQualifierFix implements IntentionAction {
  private final PsiExpression myQualifier;
  private final PsiReferenceExpression myExpression;
  private final PsiClass myResolved;

  public RemoveQualifierFix(final PsiExpression qualifier, final PsiReferenceExpression expression, final PsiClass resolved) {
    myQualifier = qualifier;
    myExpression = expression;
    myResolved = resolved;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("remove.qualifier.action.text");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return
      myQualifier != null
      && myQualifier.isValid()
      && myQualifier.getManager().isInProject(myQualifier)
      && myExpression != null
      && myExpression.isValid()
      && myResolved != null
      && myResolved.isValid()
      ;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    myQualifier.delete();
    myExpression.bindToElement(myResolved);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
