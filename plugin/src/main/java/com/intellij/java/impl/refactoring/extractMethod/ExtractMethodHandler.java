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
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.ScrollType;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.IntroduceTargetChooser;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class ExtractMethodHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(ExtractMethodHandler.class);

  public static final String REFACTORING_NAME = RefactoringBundle.message("extract.method.title");

  @Override
  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (dataContext != null) {
      final PsiFile file = dataContext.getData(PsiFile.KEY);
      final Editor editor = dataContext.getData(Editor.KEY);
      if (file != null && editor != null) {
        invokeOnElements(project, editor, file, elements);
      }
    }
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    final Consumer<PsiElement[]> callback = selectedValue -> invokeOnElements(project, editor, file, selectedValue);
    selectAndPass(project, editor, file, callback);
  }

  public static void selectAndPass(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile file, @Nonnull final Consumer<PsiElement[]> callback) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!editor.getSelectionModel().hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      final List<PsiExpression> expressions = IntroduceVariableBase.collectExpressions(file, editor, offset, true);
      if (expressions.isEmpty()) {
        editor.getSelectionModel().selectLineAtCaret();
      } else if (expressions.size() == 1) {
        callback.accept(new PsiElement[]{expressions.get(0)});
        return;
      } else {
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
    } else {
      elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (elements.length == 0) {
        final PsiExpression expression = IntroduceVariableBase.getSelectedExpression(project, file, startOffset, endOffset);
        if (expression != null && IntroduceVariableBase.getErrorMessage(expression) == null) {
          final PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expression);
          if (originalType != null) {
            elements = new PsiElement[]{expression};
          }
        }
      }
    }
    callback.accept(elements);
  }

  private static void invokeOnElements(final Project project, final Editor editor, PsiFile file, PsiElement[] elements) {
    getProcessor(elements, project, file, editor, true, processor -> invokeOnElements(project, editor, processor, true));
  }

  private static boolean invokeOnElements(final Project project, final Editor editor, @Nonnull final ExtractMethodProcessor processor, final boolean directTypes) {
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

  public static void run(@Nonnull final Project project, final Editor editor, final ExtractMethodProcessor processor) {
    CommandProcessor.getInstance().executeCommand(
      project,
      () -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
        try {
          processor.doRefactoring();
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }),
      REFACTORING_NAME,
      null
    );
  }

  @Nullable
  private static ExtractMethodProcessor getProcessor(
    final PsiElement[] elements,
    final Project project,
    final PsiFile file,
    final Editor editor,
    final boolean showErrorMessages,
    final @Nullable Consumer<ExtractMethodProcessor> pass
  ) {
    if (elements == null || elements.length == 0) {
      if (showErrorMessages) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringLocalize.selectedBlockShouldRepresentASetOfStatementsOrAnExpression().get()
        );
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_METHOD);
      }
      return null;
    }

    for (PsiElement element : elements) {
      if (element instanceof PsiStatement statement && JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
        if (showErrorMessages) {
          String message = RefactoringBundle.getCannotRefactorMessage(
            RefactoringLocalize.selectedBlockContainsInvocationOfAnotherClassConstructor().get()
          );
          CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_METHOD);
        }
        return null;
      }
    }

    final ExtractMethodProcessor processor = new ExtractMethodProcessor(project, editor, elements, null, REFACTORING_NAME, "", HelpID.EXTRACT_METHOD);
    processor.setShowErrorDialogs(showErrorMessages);
    try {
      if (!processor.prepare(pass)) {
        return null;
      }
    } catch (PrepareFailedException e) {
      if (showErrorMessages) {
        CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), REFACTORING_NAME, HelpID.EXTRACT_METHOD);
        highlightPrepareError(e, file, editor, project);
      }
      return null;
    }
    return processor;
  }

  public static void highlightPrepareError(PrepareFailedException e, PsiFile file, Editor editor, final Project project) {
    if (e.getFile() == file) {
      final TextRange textRange = e.getTextRange();
      final HighlightManager highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, true, null);
      final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(textRange.getStartOffset());
      editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
      WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringLocalize.pressEscapeToRemoveTheHighlighting().get());
    }
  }

  @Nullable
  public static ExtractMethodProcessor getProcessor(
    final Project project,
    final PsiElement[] elements,
    final PsiFile file,
    final boolean openEditor
  ) {
    return getProcessor(elements, project, file, openEditor ? openEditor(project, file) : null, false, null);
  }

  public static boolean invokeOnElements(
    final Project project,
    @Nonnull final ExtractMethodProcessor processor,
    final PsiFile file,
    final boolean directTypes
  ) {
    return invokeOnElements(project, openEditor(project, file), processor, directTypes);
  }

  @Nullable
  private static Editor openEditor(final Project project, final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    final OpenFileDescriptor fileDescriptor = OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).build();
    return FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, false);
  }
}
