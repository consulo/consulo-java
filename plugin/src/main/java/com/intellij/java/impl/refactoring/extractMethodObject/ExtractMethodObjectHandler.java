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

/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.java.impl.refactoring.extractMethodObject;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.java.impl.refactoring.extractMethod.PrepareFailedException;
import com.intellij.java.impl.refactoring.util.duplicates.DuplicatesImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.document.RangeMarker;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;

public class ExtractMethodObjectHandler implements RefactoringActionHandler {
    private static final Logger LOG = Logger.getInstance(ExtractMethodObjectHandler.class);

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        ExtractMethodHandler.selectAndPass(project, editor, file, selectedValue -> invokeOnElements(project, editor, file, selectedValue));
    }

    @RequiredUIAccess
    private void invokeOnElements(
        @Nonnull Project project,
        @Nonnull Editor editor,
        @Nonnull PsiFile file,
        @Nonnull PsiElement[] elements
    ) {
        if (elements.length == 0) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactoringLocalize.cannotPerformRefactoringWithReason(
                    RefactoringLocalize.selectedBlockShouldRepresentASetOfStatementsOrAnExpression()
                ),
                ExtractMethodObjectProcessor.REFACTORING_NAME,
                HelpID.EXTRACT_METHOD_OBJECT
            );
            return;
        }

        ExtractMethodObjectProcessor processor = new ExtractMethodObjectProcessor(project, editor, elements, "");
        ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
        try {
            if (!extractProcessor.prepare()) {
                return;
            }
        }
        catch (PrepareFailedException e) {
            CommonRefactoringUtil.showErrorHint(project, editor,
                LocalizeValue.ofNullable(e.getMessage()),
                ExtractMethodObjectProcessor.REFACTORING_NAME,
                HelpID.EXTRACT_METHOD_OBJECT
            );
            ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
            return;
        }

        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, extractProcessor.getTargetClass().getContainingFile())) {
            return;
        }
        if (extractProcessor.showDialog()) {
            run(project, editor, processor, extractProcessor);
        }
    }

    @RequiredUIAccess
    public static void run(
        @Nonnull Project project,
        @Nonnull Editor editor,
        @Nonnull ExtractMethodObjectProcessor processor,
        @Nonnull ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor
    ) {
        int offset = editor.getCaretModel().getOffset();
        RangeMarker marker = editor.getDocument().createRangeMarker(new TextRange(offset, offset));
        CommandProcessor.getInstance().newCommand()
            .project(project)
            .name(ExtractMethodObjectProcessor.REFACTORING_NAME)
            .groupId(ExtractMethodObjectProcessor.REFACTORING_NAME)
            .run(() -> {
                PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
                    try {
                        project.getApplication().runWriteAction(() -> extractProcessor.doRefactoring());
                        processor.run();
                        processor.runChangeSignature();
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }
                });

                PsiDocumentManager.getInstance(project).commitAllDocuments();
                if (processor.isCreateInnerClass()) {
                    processor.moveUsedMethodsToInner();
                    PsiDocumentManager.getInstance(project).commitAllDocuments();
                    DuplicatesImpl.processDuplicates(extractProcessor, project, editor);
                }
                project.getApplication().runWriteAction(() -> {
                    if (processor.isCreateInnerClass()) {
                        processor.changeInstanceAccess(project);
                    }
                    PsiElement method = processor.getMethod();
                    LOG.assertTrue(method != null);
                    method.delete();
                });
            });
        editor.getCaretModel().moveToOffset(marker.getStartOffset());
        marker.dispose();
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        throw new UnsupportedOperationException();
    }
}