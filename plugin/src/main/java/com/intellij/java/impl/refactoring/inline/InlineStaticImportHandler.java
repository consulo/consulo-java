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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.language.psi.PsiImportStaticStatement;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import consulo.undoRedo.UndoConfirmationPolicy;
import jakarta.annotation.Nonnull;

import java.util.List;

import static com.intellij.java.language.impl.psi.util.ImportsUtil.collectReferencesThrough;
import static com.intellij.java.language.impl.psi.util.ImportsUtil.replaceAllAndDeleteImport;

/**
 * @author anna
 * @since 2011-09-01
 */
@ExtensionImpl
public class InlineStaticImportHandler extends JavaInlineActionHandler {
    @Override
    public boolean canInlineElement(@Nonnull PsiElement element) {
        if (element.getContainingFile() == null) {
            return false;
        }
        return PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) != null;
    }

    @Override
    @RequiredUIAccess
    public void inlineElement(Project project, Editor editor, PsiElement element) {
        PsiImportStaticStatement staticStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
        List<PsiJavaCodeReferenceElement> referenceElements =
            collectReferencesThrough(element.getContainingFile(), null, staticStatement);

        CommandProcessor.getInstance().newCommand()
            .project(project)
            .name(JavaRefactoringLocalize.actionExpandStaticImportText())
            .undoConfirmationPolicy(UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION)
            .inWriteAction()
            .run(() -> replaceAllAndDeleteImport(referenceElements, null, staticStatement));
    }
}
