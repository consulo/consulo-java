/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.template.impl.InvokeTemplateAction;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.TemplateSettings;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;

/**
 * User: anna
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.IterateOverIterableIntention", categories = {"Java", "Control Flow"}, fileExtensions = "java")
public class IterateOverIterableIntention implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(IterateOverIterableIntention.class);

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    final Template template = getTemplate();
    if (template != null) {
      int offset = editor.getCaretModel().getOffset();
      int startOffset = offset;
      if (editor.getSelectionModel().hasSelection()) {
        final int selStart = editor.getSelectionModel().getSelectionStart();
        final int selEnd = editor.getSelectionModel().getSelectionEnd();
        startOffset = (offset == selStart) ? selEnd : selStart;
      }
      PsiElement element = file.findElementAt(startOffset);
      while (element instanceof PsiWhiteSpace) {
        element = element.getPrevSibling();
      }
      PsiStatement psiStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);
      if (psiStatement != null) {
        startOffset = psiStatement.getTextRange().getStartOffset();
      }
      if (!template.isDeactivated() &&
          (TemplateManager.getInstance(project).isApplicable(file, offset, template) ||
           (TemplateManager.getInstance(project).isApplicable(file, startOffset, template)))) {
        return getIterableExpression(editor, file) != null;
      }
    }
    return false;
  }

  @Nullable
  private static Template getTemplate() {
    return TemplateSettings.getInstance().getTemplate("I", "surround");
  }


  @Nonnull
  @Override
  public String getText() {
    return "Iterate";
  }
  
  @Nullable
  private static PsiExpression getIterableExpression(Editor editor, PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      PsiElement elementAtStart = file.findElementAt(selectionModel.getSelectionStart());
      PsiElement elementAtEnd = file.findElementAt(selectionModel.getSelectionEnd() - 1);
      if (elementAtStart == null || elementAtStart instanceof PsiWhiteSpace || elementAtStart instanceof PsiComment) {
        elementAtStart = PsiTreeUtil.skipSiblingsForward(elementAtStart, PsiWhiteSpace.class, PsiComment.class);
        if (elementAtStart == null) return null;
      }
      if (elementAtEnd == null || elementAtEnd instanceof PsiWhiteSpace || elementAtEnd instanceof PsiComment) {
        elementAtEnd = PsiTreeUtil.skipSiblingsBackward(elementAtEnd, PsiWhiteSpace.class, PsiComment.class);
        if (elementAtEnd == null) return null;
      }
      PsiElement parent = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
      if (parent instanceof PsiExpression) {
        final PsiType type = ((PsiExpression)parent).getType();
        return type instanceof PsiArrayType || InheritanceUtil.isInheritor(type, JavaClassNames.JAVA_LANG_ITERABLE)
               ? (PsiExpression)parent
               : null;
      }
      return null;
    }

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    while (element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }
    if (element instanceof PsiExpressionStatement) {
      element = ((PsiExpressionStatement)element).getExpression().getLastChild();
    }
    while ((element = PsiTreeUtil.getParentOfType(element, PsiExpression.class, true)) != null) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodCallExpression) continue;
      if (!(parent instanceof PsiExpressionStatement)) return null;
      final PsiType type = ((PsiExpression)element).getType();
      if (type instanceof PsiArrayType || InheritanceUtil.isInheritor(type, JavaClassNames.JAVA_LANG_ITERABLE)) return (PsiExpression)element;
    }
    return null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final Template template = getTemplate();
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final PsiExpression iterableExpression = getIterableExpression(editor, file);
      LOG.assertTrue(iterableExpression != null);
      TextRange textRange = iterableExpression.getTextRange();
      selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
    }
    new InvokeTemplateAction(template, editor, project, new HashSet<Character>()).perform();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
