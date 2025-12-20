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

import com.intellij.java.impl.codeInsight.template.macro.SuggestVariableNameMacro;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.find.FindManager;
import consulo.find.FindModel;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.template.*;
import consulo.language.editor.template.macro.MacroCallNode;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiReference;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;

public class HighlightUtils {

  private HighlightUtils() {
  }

  public static void highlightElement(PsiElement element) {
    highlightElements(Collections.singleton(element));
  }

  public static void highlightElements(
    @Nonnull Collection<? extends PsiElement> elementCollection) {
    if (elementCollection.isEmpty()) {
      return;
    }
    Application application = Application.get();
    application.invokeLater(() -> {
      PsiElement[] elements = PsiUtilBase.toPsiElementArray(elementCollection);
      PsiElement firstElement = elements[0];
      if (!firstElement.isValid()) {
        return;
      }
      Project project = firstElement.getProject();
      FileEditorManager editorManager = FileEditorManager.getInstance(project);
      Editor editor = editorManager.getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      HighlightManager highlightManager = HighlightManager.getInstance(project);
      highlightManager.addOccurrenceHighlights(editor, elements, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      WindowManager windowManager = WindowManager.getInstance();
      FindManager findmanager = FindManager.getInstance(project);
      FindModel findmodel = findmanager.getFindNextModel();
      if (findmodel == null) {
        findmodel = findmanager.getFindInFileModel();
      }
      findmodel.setSearchHighlighters(true);
      findmanager.setFindWasPerformed();
      findmanager.setFindNextModel(findmodel);
    });
  }

  @RequiredReadAction
  public static void showRenameTemplate(PsiElement context, PsiNameIdentifierOwner element, PsiReference... references) {
    context = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(context);
    Project project = context.getProject();
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    Editor editor = fileEditorManager.getSelectedTextEditor();
    if (editor == null) {
      return;
    }
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(context);
    Expression macroCallNode = new MacroCallNode(new SuggestVariableNameMacro());
    PsiElement identifier = element.getNameIdentifier();
    builder.replaceElement(identifier, "PATTERN", macroCallNode, true);
    for (PsiReference reference : references) {
      builder.replaceElement(reference, "PATTERN", "PATTERN", false);
    }
    Template template = builder.buildInlineTemplate();
    TextRange textRange = context.getTextRange();
    int startOffset = textRange.getStartOffset();
    editor.getCaretModel().moveToOffset(startOffset);
    TemplateManager templateManager = TemplateManager.getInstance(project);
    templateManager.startTemplate(editor, template);
  }
}
