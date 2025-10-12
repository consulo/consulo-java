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

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.refactoring.safeDelete.SafeDeleteHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class SafeDeleteFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    public SafeDeleteFix(@Nonnull PsiElement element) {
        super(element);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public LocalizeValue getText() {
        PsiElement startElement = getStartElement();
        return JavaQuickFixLocalize.safeDeleteText(
            startElement == null ? ""
                : HighlightMessageUtil.getSymbolName(startElement, PsiSubstitutor.EMPTY)
        );
    }

    @Override
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nullable Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        SafeDeleteHandler.invoke(project, new PsiElement[]{startElement}, false);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
