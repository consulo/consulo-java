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

import com.intellij.java.impl.refactoring.actions.TypeCookAction;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;

public class GenerifyFileFix implements SyntheticIntentionAction, LocalQuickFix {
    @Nonnull
    private LocalizeValue myText = LocalizeValue.empty();

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return myText;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return getText();
    }

    @Override
    @RequiredUIAccess
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return;
        }
        PsiFile file = element.getContainingFile();
        if (isAvailable(project, null, file)) {
            CommandProcessor.getInstance().newCommand()
                .project(project)
                .inWriteAction()
                .run(() -> invoke(project, FileEditorManager.getInstance(project).getSelectedTextEditor(), file));
        }
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (file != null && file.isValid()) {
            myText = JavaQuickFixLocalize.generifyText(file.getName());
            return PsiManager.getInstance(project).isInProject(file);
        }
        else {
            myText = LocalizeValue.empty();
            return false;
        }
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        new TypeCookAction().getHandler().invoke(project, editor, file, null);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
