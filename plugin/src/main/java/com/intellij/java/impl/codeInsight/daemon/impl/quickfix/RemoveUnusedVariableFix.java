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

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedVariableFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(RemoveUnusedVariableFix.class);
  private final PsiVariable myVariable;

  public RemoveUnusedVariableFix(PsiVariable variable) {
    myVariable = variable;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message(myVariable instanceof PsiField ? "remove.unused.field" : "remove.unused.variable",
                                  myVariable.getName());
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("remove.unused.variable.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return
      myVariable != null
      && myVariable.isValid()
      && myVariable.getManager().isInProject(myVariable)
      ;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) return;
    removeVariableAndReferencingStatements(editor);
  }

  private void removeVariableAndReferencingStatements(Editor editor) {
    final List<PsiElement> references = new ArrayList<PsiElement>();
    final List<PsiElement> sideEffects = new ArrayList<PsiElement>();
    final boolean[] canCopeWithSideEffects = {true};
    try {
      PsiElement context = myVariable instanceof PsiField ? ((PsiField)myVariable).getContainingClass() : PsiUtil.getVariableCodeBlock(myVariable, null);
      if (context != null) {
        RemoveUnusedVariableUtil.collectReferences(context, myVariable, references);
      }
      // do not forget to delete variable declaration
      references.add(myVariable);
      // check for side effects
      for (PsiElement element : references) {
        Boolean result = RemoveUnusedVariableUtil.processUsage(element, myVariable, sideEffects, RemoveUnusedVariableUtil.CANCEL);
        if (result == null) return;
        canCopeWithSideEffects[0] &= result;
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    final int deleteMode = showSideEffectsWarning(sideEffects, myVariable, editor, canCopeWithSideEffects[0]);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          RemoveUnusedVariableUtil.deleteReferences(myVariable, references, deleteMode);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  public static int showSideEffectsWarning(List<PsiElement> sideEffects,
                                           PsiVariable variable,
                                           Editor editor,
                                           boolean canCopeWithSideEffects,
                                           @NonNls String beforeText,
                                           @NonNls String afterText) {
    if (sideEffects.isEmpty()) return RemoveUnusedVariableUtil.DELETE_ALL;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return canCopeWithSideEffects
             ? RemoveUnusedVariableUtil.MAKE_STATEMENT
             : RemoveUnusedVariableUtil.DELETE_ALL;
    }
    Project project = editor.getProject();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    PsiElement[] elements = PsiUtilCore.toPsiElementArray(sideEffects);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlights(editor, elements, attributes, true, null);

    SideEffectWarningDialog dialog = new SideEffectWarningDialog(project, false, variable, beforeText, afterText, canCopeWithSideEffects);
    dialog.show();
    return dialog.getExitCode();
  }

  private static int showSideEffectsWarning(List<PsiElement> sideEffects,
                                            PsiVariable variable,
                                            Editor editor,
                                            boolean canCopeWithSideEffects) {
    String text;
    if (sideEffects.isEmpty()) {
      text = "";
    }
    else {
      final PsiElement sideEffect = sideEffects.get(0);
      if (sideEffect instanceof PsiExpression) {
        text = PsiExpressionTrimRenderer.render((PsiExpression)sideEffect);
      }
      else {
        text = sideEffect.getText();
      }
    }
    return showSideEffectsWarning(sideEffects, variable, editor, canCopeWithSideEffects, text, text);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
