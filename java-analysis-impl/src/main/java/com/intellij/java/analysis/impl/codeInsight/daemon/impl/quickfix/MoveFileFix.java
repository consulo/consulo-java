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
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import java.io.IOException;

public class MoveFileFix implements IntentionAction {
  private final VirtualFile myFile;
  private final VirtualFile myTarget;
  private final String myMessage;

  public MoveFileFix(@Nonnull VirtualFile file, @Nonnull VirtualFile target, @Nonnull @Nls String message) {
    myFile = file;
    myTarget = target;
    myMessage = message;
  }

  @Nls
  @Nonnull
  @Override
  public String getText() {
    return myMessage;
  }

  @Nls
  @Nonnull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (myFile.isValid() && myTarget.isValid()) {
      try {
        myFile.move(this, myTarget);
      } catch (IOException e) {
        throw new IncorrectOperationException("Cannot move '" + myFile.getPath() + "' into '" + myTarget.getPath() + "'", (Throwable) e);
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}