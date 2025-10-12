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

import com.intellij.java.language.psi.PsiMethod;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author Alexey Kudravtsev
 */
public class MakeMethodConstructorFix implements IntentionAction {
  private final PsiMethod myMethod;

  public MakeMethodConstructorFix(PsiMethod method) {
    myMethod = method;
  }

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return JavaQuickFixLocalize.convertMethodToConstructor();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myMethod.isValid() && myMethod.getReturnTypeElement() != null && myMethod.getManager().isInProject(myMethod);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myMethod)) return;
    myMethod.getReturnTypeElement().delete();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
