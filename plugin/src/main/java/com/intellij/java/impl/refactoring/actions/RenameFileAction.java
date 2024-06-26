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
package com.intellij.java.impl.refactoring.actions;

import com.intellij.java.language.psi.PsiClassOwner;
import consulo.application.dumb.DumbAware;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author ven
 */
public class RenameFileAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    final PsiFile file = e.getData(PsiFile.KEY);
    assert file != null;
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    final Project project = e.getData(Project.KEY);
    assert project != null;
    PsiElementRenameHandler.invoke(file, project, file, null);
  }

  public void update(AnActionEvent e) {
    PsiFile file = e.getData(PsiFile.KEY);
    Presentation presentation = e.getPresentation();
    boolean enabled = file instanceof PsiClassOwner && e.getPlace() != ActionPlaces.EDITOR_POPUP && e.getData(Project.KEY) != null;
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
    if (enabled) {
      presentation.setText("Rename File...");
      presentation.setDescription("Rename selected file");
    }
  }
}
