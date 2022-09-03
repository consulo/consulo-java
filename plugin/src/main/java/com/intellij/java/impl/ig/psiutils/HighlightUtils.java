/*
 * Copyright 2007 Bas Leijdekkers
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
package com.intellij.java.impl.ig.psiutils;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;

import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.Template;
import consulo.language.editor.impl.internal.template.TemplateBuilderImpl;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.macro.MacroCallNode;
import com.intellij.java.impl.codeInsight.template.macro.SuggestVariableNameMacro;
import consulo.find.FindManager;
import consulo.find.FindModel;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.TextAttributes;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.WindowManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiReference;
import consulo.language.editor.util.PsiUtilBase;
import com.siyeh.InspectionGadgetsBundle;

public class HighlightUtils {

  private HighlightUtils() {
  }

  public static void highlightElement(PsiElement element) {
    highlightElements(Collections.singleton(element));
  }

  public static void highlightElements(
    @Nonnull final Collection<? extends PsiElement> elementCollection) {
    if (elementCollection.isEmpty()) {
      return;
    }
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {
      public void run() {
        final PsiElement[] elements =
          PsiUtilBase.toPsiElementArray(elementCollection);
        final PsiElement firstElement = elements[0];
        if (!firstElement.isValid()) {
          return;
        }
        final Project project = firstElement.getProject();
        final FileEditorManager editorManager =
          FileEditorManager.getInstance(project);
        final EditorColorsManager editorColorsManager =
          EditorColorsManager.getInstance();
        final Editor editor = editorManager.getSelectedTextEditor();
        if (editor == null) {
          return;
        }
        final EditorColorsScheme globalScheme =
          editorColorsManager.getGlobalScheme();
        final TextAttributes textattributes =
          globalScheme.getAttributes(
            EditorColors.SEARCH_RESULT_ATTRIBUTES);
        final HighlightManager highlightManager =
          HighlightManager.getInstance(project);
        highlightManager.addOccurrenceHighlights(
          editor, elements, textattributes, true, null);
        final WindowManager windowManager =
          WindowManager.getInstance();
        final StatusBar statusBar =
          windowManager.getStatusBar(project);
        statusBar.setInfo(InspectionGadgetsBundle.message(
          "press.escape.to.remove.highlighting.message"));
        final FindManager findmanager =
          FindManager.getInstance(project);
        FindModel findmodel = findmanager.getFindNextModel();
        if (findmodel == null) {
          findmodel = findmanager.getFindInFileModel();
        }
        findmodel.setSearchHighlighters(true);
        findmanager.setFindWasPerformed();
        findmanager.setFindNextModel(findmodel);
      }
    });
  }

  public static void showRenameTemplate(PsiElement context,
                                        PsiNameIdentifierOwner element,
                                        PsiReference... references) {
    context = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(
      context);
    final Project project = context.getProject();
    final FileEditorManager fileEditorManager =
      FileEditorManager.getInstance(project);
    final Editor editor = fileEditorManager.getSelectedTextEditor();
    if (editor == null) {
      return;
    }
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(context);
    final Expression macroCallNode = new MacroCallNode(
      new SuggestVariableNameMacro());
    final PsiElement identifier = element.getNameIdentifier();
    builder.replaceElement(identifier, "PATTERN", macroCallNode, true);
    for (PsiReference reference : references) {
      builder.replaceElement(reference, "PATTERN", "PATTERN",
                             false);
    }
    final Template template = builder.buildInlineTemplate();
    final TextRange textRange = context.getTextRange();
    final int startOffset = textRange.getStartOffset();
    editor.getCaretModel().moveToOffset(startOffset);
    final TemplateManager templateManager =
      TemplateManager.getInstance(project);
    templateManager.startTemplate(editor, template);
  }
}