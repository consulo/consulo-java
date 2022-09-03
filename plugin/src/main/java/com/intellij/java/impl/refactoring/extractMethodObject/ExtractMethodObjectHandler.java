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

import javax.annotation.Nonnull;

import consulo.dataContext.DataContext;
import consulo.application.ApplicationManager;
import consulo.undoRedo.CommandProcessor;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.codeEditor.ScrollType;
import consulo.project.Project;
import com.intellij.openapi.util.Pass;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.intellij.java.impl.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.java.impl.refactoring.extractMethod.PrepareFailedException;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.intellij.java.impl.refactoring.util.duplicates.DuplicatesImpl;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

public class ExtractMethodObjectHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(ExtractMethodObjectHandler.class);

  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, new Pass<PsiElement[]>() {
      public void pass(final PsiElement[] selectedValue) {
        invokeOnElements(project, editor, file, selectedValue);
      }
    });
  }

  private void invokeOnElements(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull PsiFile file, @Nonnull PsiElement[] elements) {
    if (elements.length == 0) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, ExtractMethodObjectProcessor.REFACTORING_NAME, HelpID.EXTRACT_METHOD_OBJECT);
      return;
    }

    final ExtractMethodObjectProcessor processor = new ExtractMethodObjectProcessor(project, editor, elements, "");
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    try {
      if (!extractProcessor.prepare()) return;
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), ExtractMethodObjectProcessor.REFACTORING_NAME, HelpID.EXTRACT_METHOD_OBJECT);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, extractProcessor.getTargetClass().getContainingFile())) return;
    if (extractProcessor.showDialog()) {
      run(project, editor, processor, extractProcessor);
    }
  }

  public static void run(@Nonnull final Project project,
                  @Nonnull final Editor editor,
                  @Nonnull final ExtractMethodObjectProcessor processor,
                  @Nonnull final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor) {
    final int offset = editor.getCaretModel().getOffset();
    final RangeMarker marker = editor.getDocument().createRangeMarker(new TextRange(offset, offset));
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable() {
          public void run() {
            try {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  extractProcessor.doRefactoring();
                }
              });
              processor.run();
              processor.runChangeSignature();
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (processor.isCreateInnerClass()) {
          processor.moveUsedMethodsToInner();
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          DuplicatesImpl.processDuplicates(extractProcessor, project, editor);
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (processor.isCreateInnerClass()) {
              processor.changeInstanceAccess(project);
            }
            final PsiElement method = processor.getMethod();
            LOG.assertTrue(method != null);
            method.delete();
          }
        });
      }
    }, ExtractMethodObjectProcessor.REFACTORING_NAME, ExtractMethodObjectProcessor.REFACTORING_NAME);
    editor.getCaretModel().moveToOffset(marker.getStartOffset());
    marker.dispose();
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  public void invoke(@Nonnull final Project project, @Nonnull final PsiElement[] elements, final DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}