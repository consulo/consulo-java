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
package com.intellij.java.impl.codeInsight.intention.impl;

import jakarta.annotation.Nonnull;

import consulo.application.AllIcons;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.action.BaseRefactoringIntentionAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.util.IncorrectOperationException;
import consulo.ui.image.Image;

/**
 * User: anna
 * Date: 9/5/11
 */
public class RunRefactoringAction extends BaseRefactoringIntentionAction implements SyntheticIntentionAction {
  private final RefactoringActionHandler myHandler;
  private final String myCommandName;

  public RunRefactoringAction(RefactoringActionHandler handler, String commandName) {
    myHandler = handler;
    myCommandName = commandName;
  }

  @Nonnull
  @Override
  public String getText() {
    return myCommandName;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @jakarta.annotation.Nonnull PsiElement element) throws IncorrectOperationException {
    myHandler.invoke(project, editor, element.getContainingFile(), null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public Image getIcon(@IconFlags int flags) {
    return AllIcons.Actions.RefactoringBulb;
  }
}
