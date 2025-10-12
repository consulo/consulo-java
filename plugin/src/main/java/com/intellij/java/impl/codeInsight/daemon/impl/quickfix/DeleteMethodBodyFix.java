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
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class DeleteMethodBodyFix implements SyntheticIntentionAction {
  private final PsiMethod myMethod;

  public DeleteMethodBodyFix(PsiMethod method) {
    myMethod = method;
  }

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return JavaQuickFixLocalize.deleteBodyText();
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
