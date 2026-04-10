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
package com.intellij.java.impl.codeInsight.generation.actions;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.action.CodeInsightAction;
import consulo.language.editor.generation.GenerateActionPopupTemplateInjector;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.editor.refactoring.ContextAwareActionHandler;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnAction;
import consulo.dataContext.DataContext;
import consulo.ui.ex.action.Presentation;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;

import org.jspecify.annotations.Nullable;

public class BaseGenerateAction extends CodeInsightAction implements GenerateActionPopupTemplateInjector {
    private final CodeInsightActionHandler myHandler;

    public BaseGenerateAction(CodeInsightActionHandler handler, LocalizeValue text) {
        myHandler = handler;
        getTemplatePresentation().setText(text);
    }

    @Override
    protected void update(
        Presentation presentation,
        Project project,
        Editor editor,
        PsiFile file,
        DataContext dataContext,
        @Nullable String actionPlace
    ) {
        super.update(presentation, project, editor, file, dataContext, actionPlace);
        if (myHandler instanceof ContextAwareActionHandler contextAwareHandler && presentation.isEnabled()) {
            presentation.setEnabled(contextAwareHandler.isAvailableForQuickList(editor, file, dataContext));
        }
    }

    @Override
    @Nullable
    public AnAction createEditTemplateAction(DataContext dataContext) {
        return null;
    }

    @Override
    protected final CodeInsightActionHandler getHandler() {
        return myHandler;
    }

    @Nullable
    @RequiredReadAction
    protected PsiClass getTargetClass(Editor editor, PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }
        PsiClass target = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        return target instanceof SyntheticElement ? null : target;
    }

    @Override
    @RequiredReadAction
    protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }
        if (file instanceof PsiCompiledElement) {
            return false;
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        PsiClass targetClass = getTargetClass(editor, file);
        return targetClass != null && isValidForClass(targetClass);
    }

    protected boolean isValidForClass(PsiClass targetClass) {
        return !targetClass.isInterface();
    }
}
