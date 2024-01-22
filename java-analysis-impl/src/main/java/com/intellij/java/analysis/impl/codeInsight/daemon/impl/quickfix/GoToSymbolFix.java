/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

public class GoToSymbolFix implements SyntheticIntentionAction {
  private final SmartPsiElementPointer<NavigatablePsiElement> myPointer;
  private final String myMessage;

  public GoToSymbolFix(@jakarta.annotation.Nonnull NavigatablePsiElement symbol, @Nonnull @Nls String message) {
    myPointer = SmartPointerManager.getInstance(symbol.getProject()).createSmartPsiElementPointer(symbol);
    myMessage = message;
  }

  @Nls
  @Nonnull
  @Override
  public String getText() {
    return myMessage;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myPointer.getElement() != null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    NavigatablePsiElement e = myPointer.getElement();
    if (e != null && e.isValid()) {
      e.navigate(true);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}