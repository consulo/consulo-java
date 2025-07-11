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
package com.intellij.java.impl.refactoring.introduceVariable;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.language.psi.PsiExpression;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;

public class IntroduceVariableHandler extends IntroduceVariableBase implements JavaIntroduceVariableHandlerBase {

  public void invoke(@Nonnull final Project project, final Editor editor, final PsiExpression expression) {
    invokeImpl(project, expression, editor);
  }

  @Override
  public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                               PsiExpression expr, PsiExpression[] occurrences,
                                               TypeSelectorManagerImpl typeSelectorManager,
                                               boolean declareFinalIfAll,
                                               boolean anyAssignmentLHS,
                                               final InputValidator validator,
                                               PsiElement anchor, final OccurrencesChooser.ReplaceChoice replaceChoice) {
    if (replaceChoice != null) {
      return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS, validator,
                               anchor, replaceChoice);
    }
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    HighlightManager highlightManager = null;
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      if (occurrences.length > 1 ) {
        highlightManager.addOccurrenceHighlights(editor, occurrences, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
      }
    }

    IntroduceVariableDialog dialog = new IntroduceVariableDialog(
            project, expr, occurrences.length, anyAssignmentLHS, declareFinalIfAll,
            typeSelectorManager,
            validator);
    dialog.show();
    if (!dialog.isOK()) {
    } else {
      if (editor != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return dialog;
  }

  protected void showErrorMessage(final Project project, Editor editor, String message) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE);
  }

  protected boolean reportConflicts(final MultiMap<PsiElement,String> conflicts, final Project project, IntroduceVariableSettings dialog) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    final boolean ok = conflictsDialog.isOK();
    if (!ok && conflictsDialog.isShowConflicts()) {
      if (dialog instanceof DialogWrapper) ((DialogWrapper)dialog).close(DialogWrapper.CANCEL_EXIT_CODE);
    }
    return ok;
  }
}
