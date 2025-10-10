/*
 * Copyright 2007-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.psiutils;

import com.intellij.java.impl.codeInsight.template.macro.SuggestVariableNameMacro;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.util.query.Query;
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
import consulo.language.psi.*;
import consulo.language.psi.search.ReferencesSearch;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HighlightUtil {

  private HighlightUtil() {
  }

  public static void highlightElements(@Nonnull final Collection<? extends PsiElement> elementCollection) {
    if (elementCollection.isEmpty()) {
      return;
    }
    final Application application = Application.get();
    application.invokeLater(() -> {
      final PsiElement[] elements = PsiUtilCore.toPsiElementArray(elementCollection);
      final PsiElement firstElement = elements[0];
      if (!firstElement.isValid()) {
        return;
      }
      final Project project = firstElement.getProject();
      final FileEditorManager editorManager = FileEditorManager.getInstance(project);
      final Editor editor = editorManager.getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      final HighlightManager highlightManager = HighlightManager.getInstance(project);
      highlightManager.addOccurrenceHighlights(editor, elements, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      final FindManager findmanager = FindManager.getInstance(project);
      FindModel findmodel = findmanager.getFindNextModel();
      if (findmodel == null) {
        findmodel = findmanager.getFindInFileModel();
      }
      findmodel.setSearchHighlighters(true);
      findmanager.setFindWasPerformed();
      findmanager.setFindNextModel(findmodel);
    });
  }

  public static void highlightElement(@Nonnull PsiElement element) {
    final List<PsiElement> elements = Collections.singletonList(element);
    highlightElements(elements);
  }

  @RequiredReadAction
  public static String getPresentableText(PsiElement element) {
    return getPresentableText(element, new StringBuilder()).toString();
  }

  @RequiredReadAction
  private static StringBuilder getPresentableText(PsiElement element, StringBuilder builder) {
    if (element == null) {
      return builder;
    }
    if (element instanceof PsiWhiteSpace) {
      return builder.append(' ');
    }
    final PsiElement[] children = element.getChildren();
    if (children.length != 0) {
      for (PsiElement child : children) {
        getPresentableText(child, builder);
      }
    }
    else {
      builder.append(element.getText());
    }
    return builder;
  }

  @RequiredReadAction
  public static void showRenameTemplate(PsiElement context, PsiNameIdentifierOwner element) {
    context = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(context);
    final Query<PsiReference> query = ReferencesSearch.search(element, element.getUseScope());
    final Collection<PsiReference> references = query.findAll();
    final Project project = context.getProject();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    final Editor editor = fileEditorManager.getSelectedTextEditor();
    if (editor == null) {
      return;
    }
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(context);
    final Expression macroCallNode = new MacroCallNode(new SuggestVariableNameMacro());
    final PsiElement identifier = element.getNameIdentifier();
    builder.replaceElement(identifier, "PATTERN", macroCallNode, true);
    for (PsiReference reference : references) {
      builder.replaceElement(reference, "PATTERN", "PATTERN", false);
    }
    final Template template = builder.buildInlineTemplate();
    final TextRange textRange = context.getTextRange();
    final int startOffset = textRange.getStartOffset();
    editor.getCaretModel().moveToOffset(startOffset);
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    templateManager.startTemplate(editor, template);
  }
}