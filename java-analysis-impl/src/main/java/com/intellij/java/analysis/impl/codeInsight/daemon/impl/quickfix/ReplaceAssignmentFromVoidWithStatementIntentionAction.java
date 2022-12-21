/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiExpression;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;

public class ReplaceAssignmentFromVoidWithStatementIntentionAction implements SyntheticIntentionAction {
  private final PsiElement myParent;
  private final PsiExpression myLExpr;

  public ReplaceAssignmentFromVoidWithStatementIntentionAction(PsiElement parent, PsiExpression lExpr) {
    myParent = parent;
    myLExpr = lExpr;
  }

  @Nls
  @Nonnull
  @Override
  public String getText() {
    return "Remove left side of assignment";
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
      return;
    }
    myParent.replace(myLExpr);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
