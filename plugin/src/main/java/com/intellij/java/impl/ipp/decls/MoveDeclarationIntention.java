/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.decls;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MoveDeclarationIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class MoveDeclarationIntention extends Intention {

  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new MoveDeclarationPredicate();
  }

  public void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiLocalVariable variable = (PsiLocalVariable)element;
    final PsiReference[] references = ReferencesSearch.search(variable, variable.getUseScope(), false).toArray(PsiReference.EMPTY_ARRAY);
    final PsiCodeBlock tightestBlock =
      MoveDeclarationPredicate.getTightestBlock(references);
    assert tightestBlock != null;
    final PsiDeclarationStatement declaration =
      (PsiDeclarationStatement)variable.getParent();
    final PsiReference firstReference = references[0];
    final PsiElement referenceElement = firstReference.getElement();
    PsiDeclarationStatement newDeclaration;
    if (tightestBlock.equals(PsiTreeUtil.getParentOfType(referenceElement,
                                                         PsiCodeBlock.class))) {
      // containing block of first reference is the same as the common
      //  block of all.
      newDeclaration = moveDeclarationToReference(referenceElement,
                                                  variable,
                                                  tightestBlock);
    }
    else {
      // declaration must be moved to common block (first reference block
      // is too deep)
      final PsiElement child =
        MoveDeclarationPredicate.getChildWhichContainsElement(
          tightestBlock, referenceElement);
      newDeclaration = createNewDeclaration(variable, null);
      newDeclaration = (PsiDeclarationStatement)
        tightestBlock.addBefore(newDeclaration, child);
    }
    assert declaration != null;
    if (declaration.getDeclaredElements().length == 1) {
      declaration.delete();
    }
    else {
      variable.delete();
    }
    highlightElement(newDeclaration);
  }

  private static void highlightElement(@Nonnull PsiElement element) {
    final Project project = element.getProject();
    final FileEditorManager editorManager =
      FileEditorManager.getInstance(project);
    final HighlightManager highlightManager =
      HighlightManager.getInstance(project);
    final Editor editor = editorManager.getSelectedTextEditor();
    final PsiElement[] elements = new PsiElement[]{element};
    highlightManager.addOccurrenceHighlights(editor, elements, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
  }

  private static PsiDeclarationStatement moveDeclarationToReference(
    @Nonnull PsiElement referenceElement,
    @Nonnull PsiLocalVariable variable,
    @Nonnull PsiCodeBlock block)
    throws IncorrectOperationException {
    PsiStatement statement =
      PsiTreeUtil.getParentOfType(referenceElement,
                                  PsiStatement.class);
    assert statement != null;
    if (statement.getParent() instanceof PsiForStatement) {
      statement = (PsiStatement)statement.getParent();
    }
    final PsiElement referenceParent = referenceElement.getParent();
    if (referenceParent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)referenceParent;
      if (referenceElement.equals(
        assignmentExpression.getLExpression())) {
        PsiDeclarationStatement newDeclaration =
          createNewDeclaration(variable,
                               assignmentExpression.getRExpression());
        newDeclaration = (PsiDeclarationStatement)
          block.addBefore(newDeclaration,
                          statement);
        final PsiElement parent = assignmentExpression.getParent();
        assert parent != null;
        parent.delete();
        return newDeclaration;
      }
    }
    return createNewDeclaration(variable, null);
  }

  private static PsiDeclarationStatement createNewDeclaration(
    @Nonnull PsiLocalVariable variable, PsiExpression initializer)
    throws IncorrectOperationException {
    final PsiManager manager = variable.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final PsiDeclarationStatement newDeclaration =
      factory.createVariableDeclarationStatement(
        variable.getName(), variable.getType(), initializer);
    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
      final PsiLocalVariable newVariable =
        (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
      final PsiModifierList modifierList = newVariable.getModifierList();
      modifierList.setModifierProperty(PsiModifier.FINAL, true);
    }
    return newDeclaration;
  }
}