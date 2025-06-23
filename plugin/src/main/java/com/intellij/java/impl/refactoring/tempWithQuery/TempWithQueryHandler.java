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
package com.intellij.java.impl.refactoring.tempWithQuery;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.java.impl.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.java.impl.refactoring.extractMethod.PrepareFailedException;
import com.intellij.java.impl.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiDeclarationStatement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.TargetElementUtilExtender;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Set;

public class TempWithQueryHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(TempWithQueryHandler.class);

  private static final String REFACTORING_NAME = RefactoringBundle.message("replace.temp.with.query.title");

  @RequiredUIAccess
  public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    Set<String> flags = Set.of(
      TargetElementUtilExtender.ELEMENT_NAME_ACCEPTED,
      TargetElementUtilExtender.REFERENCED_ELEMENT_ACCEPTED,
      TargetElementUtilExtender.LOOKUP_ITEM_ACCEPTED
    );
    PsiElement element = TargetElementUtil.findTargetElement(editor, flags);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!(element instanceof PsiLocalVariable)) {
      LocalizeValue message =
        RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionLocalName());
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    invokeOnVariable(file, project, (PsiLocalVariable) element, editor);
  }

  @RequiredUIAccess
  private static void invokeOnVariable(final PsiFile file, final Project project, final PsiLocalVariable local, final Editor editor) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
      return;
    }

    String localName = local.getName();
    final PsiExpression initializer = local.getInitializer();
    if (initializer == null) {
      LocalizeValue message =
        RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.variableHasNoInitializer(localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    final PsiReference[] refs = ReferencesSearch.search(local, GlobalSearchScope.projectScope(project), false)
      .toArray(new PsiReference[0]);

    if (refs.length == 0) {
      LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.variableIsNeverUsed(localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    ArrayList<PsiReference> array = new ArrayList<>();
    for (PsiReference ref : refs) {
      PsiElement refElement = ref.getElement();
      if (PsiUtil.isAccessedForWriting((PsiExpression) refElement)) {
        array.add(ref);
      }
      if (!array.isEmpty()) {
        PsiReference[] refsForWriting = array.toArray(new PsiReference[array.size()]);
        highlightManager.addOccurrenceHighlights(editor, refsForWriting, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        LocalizeValue message =
          RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.variableIsAccessedForWriting(localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
        return;
      }
    }

    final ExtractMethodProcessor processor = new ExtractMethodProcessor(project, editor, new PsiElement[]{initializer}, local.getType(),
        REFACTORING_NAME, localName, HelpID.REPLACE_TEMP_WITH_QUERY);

    try {
      if (!processor.prepare()) {
        return;
      }
    } catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
      return;
    }
    final PsiClass targetClass = processor.getTargetClass();
    if (targetClass != null && targetClass.isInterface()) {
      LocalizeValue message = RefactoringLocalize.cannotReplaceTempWithQueryInInterface();
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    if (processor.showDialog()) {
      CommandProcessor.getInstance().executeCommand(project, () -> {
        final Runnable action = () -> {
          try {
            processor.doRefactoring();

            local.normalizeDeclaration();

            PsiExpression initializer1 = local.getInitializer();

            PsiExpression[] exprs = new PsiExpression[refs.length];
            for (int idx = 0; idx < refs.length; idx++) {
              PsiElement ref = refs[idx].getElement();
              exprs[idx] = (PsiExpression) ref.replace(initializer1);
            }
            PsiDeclarationStatement declaration = (PsiDeclarationStatement) local.getParent();
            declaration.delete();

            highlightManager.addOccurrenceHighlights(editor, exprs, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
          } catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        };

        PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
          project.getApplication().runWriteAction(action);
          DuplicatesImpl.processDuplicates(processor, project, editor);
        });
      }, REFACTORING_NAME, null);
    }
  }

  @RequiredUIAccess
  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (elements.length == 1 && elements[0] instanceof PsiLocalVariable) {
      if (dataContext != null) {
        final PsiFile file = dataContext.getData(PsiFile.KEY);
        final Editor editor = dataContext.getData(Editor.KEY);
        if (file != null && editor != null) {
          invokeOnVariable(file, project, (PsiLocalVariable) elements[0], editor);
        }
      }
    }
  }
}