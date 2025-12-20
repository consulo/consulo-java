/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.RenameWrongRefFix;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.rename.RenameHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class RenameWrongRefHandler implements RenameHandler {
    @Override
    @RequiredReadAction
    public final boolean isAvailableOnDataContext(DataContext dataContext) {
        Editor editor = dataContext.getData(Editor.KEY);
        PsiFile file = dataContext.getData(PsiFile.KEY);
        Project project = dataContext.getData(Project.KEY);
        return !(editor == null || file == null || project == null) && isAvailable(project, editor, file);
    }

    @Nonnull
    @Override
    public LocalizeValue getActionTitleValue() {
        return LocalizeValue.localizeTODO("Rename Wrong Reference...");
    }

    @RequiredReadAction
    public static boolean isAvailable(Project project, Editor editor, PsiFile file) {
        PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
        return reference instanceof PsiReferenceExpression referenceExpression
            && new RenameWrongRefFix(referenceExpression, true).isAvailable(project, editor, file);
    }

    @Override
    @RequiredReadAction
    public final boolean isRenaming(DataContext dataContext) {
        return isAvailableOnDataContext(dataContext);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
        final PsiReferenceExpression reference = (PsiReferenceExpression) file.findReferenceAt(editor.getCaretModel().getOffset());
        new WriteCommandAction(project) {
            @Override
            protected void run(Result result) throws Throwable {
                new RenameWrongRefFix(reference).invoke(project, editor, file);
            }
        }.execute();
    }

    @Override
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    }
}
