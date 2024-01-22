/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import jakarta.annotation.Nonnull;

import consulo.language.editor.FileModificationService;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.util.IncorrectOperationException;

public class RemoveParameterListFix implements SyntheticIntentionAction {

  private final PsiMethod myMethod;

  public RemoveParameterListFix(PsiMethod method) {
    myMethod = method;
  }

  @Override
  @Nonnull
  public String getText() {
    return "Remove parameter list";
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myMethod != null && myMethod.isValid();
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiMethod emptyMethod = JavaPsiFacade.getElementFactory(project).createMethodFromText("void foo(){}", myMethod);
    myMethod.getParameterList().replace(emptyMethod.getParameterList());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
