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
package com.intellij.java.impl.refactoring.extractMethod;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.introduce.IntroduceTargetChooser;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class ExtractMethodHandler implements RefactoringActionHandler {
    private static final Logger LOG = Logger.getInstance(ExtractMethodHandler.class);

    public static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.extractMethodTitle();

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        if (dataContext != null) {
            PsiFile file = dataContext.getData(PsiFile.KEY);
            Editor editor = dataContext.getData(Editor.KEY);
            if (file != null && editor != null) {
                invokeOnElements(project, editor, file, elements);
            }
        }
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        @RequiredUIAccess
        Consumer<PsiElement[]> callback = selectedValue -> invokeOnElements(project, editor, file, selectedValue);
        selectAndPass(project, editor, file, callback);
    }

    @RequiredReadAction
    public static void selectAndPass(
        @Nonnull Project project,
        @Nonnull Editor editor,
        @Nonnull PsiFile file,
        @Nonnull @RequiredUIAccess Consumer<PsiElement[]> callback
    ) {
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        if (!editor.getSelectionModel().hasSelection()) {
            int offset = editor.getCaretModel().getOffset();
            List<PsiExpression> expressions = IntroduceVariableBase.collectExpressions(file, editor, offset, true);
            if (expressions.isEmpty()) {
                editor.getSelectionModel().selectLineAtCaret();
            }
            else if (expressions.size() == 1) {
                callback.accept(new PsiElement[]{expressions.get(0)});
                return;
            }
            else {
                IntroduceTargetChooser.showChooser(
                    editor,
                    expressions,
                    psiExpression -> callback.accept(new PsiElement[]{psiExpression}),
                    new PsiExpressionTrimRenderer.RenderFunction()
                );
                return;
            }
        }

        int startOffset = editor.getSelectionModel().getSelectionStart();
        int endOffset = editor.getSelectionModel().getSelectionEnd();

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        PsiElement[] elements;
        PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
        if (expr != null) {
            elements = new PsiElement[]{expr};
        }
        else {
            elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
            if (elements.length == 0) {
                PsiExpression expression = IntroduceVariableBase.getSelectedExpression(project, file, startOffset, endOffset);
                if (expression != null && IntroduceVariableBase.getErrorMessage(expression) == null) {
                    PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expression);
                    if (originalType != null) {
                        elements = new PsiElement[]{expression};
                    }
                }
            }
        }
        callback.accept(elements);
    }

    @RequiredUIAccess
    private static void invokeOnElements(Project project, Editor editor, PsiFile file, PsiElement[] elements) {
        getProcessor(elements, project, file, editor, true, processor -> invokeOnElements(project, editor, processor, true));
    }

    @RequiredUIAccess
    private static boolean invokeOnElements(
        Project project,
        Editor editor,
        @Nonnull ExtractMethodProcessor processor,
        boolean directTypes
    ) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, processor.getTargetClass().getContainingFile())) {
            return false;
        }
        if (processor.showDialog(directTypes)) {
            run(project, editor, processor);
            DuplicatesImpl.processDuplicates(processor, project, editor);
            return true;
        }
        return false;
    }

    public static void run(@Nonnull Project project, Editor editor, ExtractMethodProcessor processor) {
        CommandProcessor.getInstance().newCommand()
            .project(project)
            .name(REFACTORING_NAME)
            .run(() -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
                try {
                    processor.doRefactoring();
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }));
    }

    @Nullable
    @RequiredUIAccess
    private static ExtractMethodProcessor getProcessor(
        PsiElement[] elements,
        Project project,
        PsiFile file,
        Editor editor,
        boolean showErrorMessages,
        @Nullable @RequiredUIAccess Consumer<ExtractMethodProcessor> pass
    ) {
        if (elements == null || elements.length == 0) {
            if (showErrorMessages) {
                LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                    RefactoringLocalize.selectedBlockShouldRepresentASetOfStatementsOrAnExpression()
                );
                CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_METHOD);
            }
            return null;
        }

        for (PsiElement element : elements) {
            if (element instanceof PsiStatement statement && JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
                if (showErrorMessages) {
                    LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                        RefactoringLocalize.selectedBlockContainsInvocationOfAnotherClassConstructor()
                    );
                    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_METHOD);
                }
                return null;
            }
        }

        ExtractMethodProcessor processor =
            new ExtractMethodProcessor(project, editor, elements, null, REFACTORING_NAME, "", HelpID.EXTRACT_METHOD);
        processor.setShowErrorDialogs(showErrorMessages);
        try {
            if (!processor.prepare(pass)) {
                return null;
            }
        }
        catch (PrepareFailedException e) {
            if (showErrorMessages) {
                CommonRefactoringUtil.showErrorHint(
                    project,
                    editor,
                    LocalizeValue.ofNullable(e.getMessage()),
                    REFACTORING_NAME,
                    HelpID.EXTRACT_METHOD
                );
                highlightPrepareError(e, file, editor, project);
            }
            return null;
        }
        return processor;
    }

    public static void highlightPrepareError(PrepareFailedException e, PsiFile file, Editor editor, Project project) {
        if (e.getFile() == file) {
            TextRange textRange = e.getTextRange();
            HighlightManager highlightManager = HighlightManager.getInstance(project);
            highlightManager.addRangeHighlight(
                editor,
                textRange.getStartOffset(),
                textRange.getEndOffset(),
                EditorColors.SEARCH_RESULT_ATTRIBUTES,
                true,
                null
            );
            LogicalPosition logicalPosition = editor.offsetToLogicalPosition(textRange.getStartOffset());
            editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
        }
    }

    @Nullable
    @RequiredUIAccess
    public static ExtractMethodProcessor getProcessor(Project project, PsiElement[] elements, PsiFile file, boolean openEditor) {
        return getProcessor(elements, project, file, openEditor ? openEditor(project, file) : null, false, null);
    }

    @RequiredUIAccess
    public static boolean invokeOnElements(Project project, @Nonnull ExtractMethodProcessor processor, PsiFile file, boolean directTypes) {
        return invokeOnElements(project, openEditor(project, file), processor, directTypes);
    }

    @Nullable
    private static Editor openEditor(Project project, PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        OpenFileDescriptor fileDescriptor = OpenFileDescriptorFactory.getInstance(project).newBuilder(virtualFile).build();
        return FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, false);
    }
}
