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

import com.intellij.codeInsight.FileModificationService;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import javax.annotation.Nonnull;

/**
 * @author ven
 */
public class DeleteMethodBodyFix implements IntentionAction {
  private final PsiMethod myMethod;

  public DeleteMethodBodyFix(PsiMethod method) {
    myMethod = method;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("delete.body.text");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("delete.body.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myMethod.isValid() && myMethod.getManager().isInProject(myMethod) && myMethod.getBody() != null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myMethod)) return;
    final PsiCodeBlock body = myMethod.getBody();
    assert body != null;
    body.delete();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
