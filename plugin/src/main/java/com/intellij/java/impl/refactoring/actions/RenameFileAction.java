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
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author ven
 */
@ActionImpl(
    id = "RenameFile",
    parents = {
        @ActionParentRef(
            value = @ActionRef(id = IdeActions.GROUP_REFACTOR),
            anchor = ActionRefAnchor.AFTER,
            relatedToAction = @ActionRef(id = "RenameElement")
        ),
        @ActionParentRef(
            value = @ActionRef(id = "EditorTabPopupMenuEx"),
            anchor = ActionRefAnchor.AFTER,
            relatedToAction = @ActionRef(id = "AddAllToFavorites")
        )
    }
)
public class RenameFileAction extends AnAction implements DumbAware {
    public RenameFileAction() {
        super(JavaRefactoringLocalize.actionRenameFileText(), JavaRefactoringLocalize.actionRenameFileDescription());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        PsiFile file = e.getRequiredData(PsiFile.KEY);
        VirtualFile virtualFile = file.getVirtualFile();
        assert virtualFile != null;
        Project project = e.getRequiredData(Project.KEY);
        PsiElementRenameHandler.invoke(file, project, file, null);
    }

    @Override
    public void update(AnActionEvent e) {
        PsiFile file = e.getData(PsiFile.KEY);
        boolean enabled = file instanceof PsiClassOwner && e.getPlace() != ActionPlaces.EDITOR_POPUP && e.hasData(Project.KEY);
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
