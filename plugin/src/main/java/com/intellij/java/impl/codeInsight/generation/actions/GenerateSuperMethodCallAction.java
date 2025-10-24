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
package com.intellij.java.impl.codeInsight.generation.actions;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.java.localize.JavaLocalize;
import jakarta.annotation.Nonnull;

import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.impl.action.BaseCodeInsightAction;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiMethod;

@ActionImpl(id = "GenerateSuperMethodCall")
public class GenerateSuperMethodCallAction extends BaseCodeInsightAction {
    public GenerateSuperMethodCallAction() {
        super(JavaLocalize.actionGenerateSuperMethodCallText(), JavaLocalize.actionGenerateSuperMethodCallDescription());
    }

    @Nonnull
    @Override
    protected CodeInsightActionHandler getHandler() {
        return new GenerateSuperMethodCallHandler();
    }

    @Override
    @RequiredReadAction
    protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }
        PsiMethod method = GenerateSuperMethodCallHandler.canInsertSuper(project, editor, file);
        return method != null;
    }
}