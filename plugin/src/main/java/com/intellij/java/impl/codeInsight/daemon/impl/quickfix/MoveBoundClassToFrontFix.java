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

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiReferenceList;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class MoveBoundClassToFrontFix extends ExtendsListFix {
    private static final Logger LOG = Logger.getInstance(MoveBoundClassToFrontFix.class);
    @Nonnull
    private final LocalizeValue myName;

    public MoveBoundClassToFrontFix(PsiClass aClass, PsiClassType classToExtendFrom) {
        super(aClass, classToExtendFrom, true);
        myName = JavaQuickFixLocalize.moveBoundClassToFrontFixText(
            HighlightUtil.formatClass(myClassToExtendFrom),
            HighlightUtil.formatClass(aClass)
        );
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return myName;
    }

    @Override
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nullable Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiClass myClass = (PsiClass) startElement;
        if (!FileModificationService.getInstance().prepareFileForWrite(myClass.getContainingFile())) {
            return;
        }
        PsiReferenceList extendsList = myClass.getExtendsList();
        if (extendsList == null) {
            return;
        }
        try {
            modifyList(extendsList, false, -1);
            modifyList(extendsList, true, 0);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        LanguageUndoUtil.markPsiFileForUndo(file);
    }

    @Override
    public boolean isAvailable(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiClass myClass = (PsiClass) startElement;
        return
            myClass.isValid()
                && myClass.getManager().isInProject(myClass)
                && myClassToExtendFrom != null
                && myClassToExtendFrom.isValid();
    }
}
