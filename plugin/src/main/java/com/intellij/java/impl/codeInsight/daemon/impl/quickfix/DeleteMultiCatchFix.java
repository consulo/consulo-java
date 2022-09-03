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

import consulo.language.editor.FileModificationService;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiTypeElement;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import consulo.language.editor.intention.IntentionAction;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import javax.annotation.Nonnull;

import java.util.List;

public class DeleteMultiCatchFix implements IntentionAction {
  private final PsiTypeElement myTypeElement;

  public DeleteMultiCatchFix(@Nonnull PsiTypeElement typeElement) {
    myTypeElement = typeElement;
  }

  @Nonnull
  @Override
  public String getText() {
    return JavaQuickFixBundle.message("delete.catch.text", JavaHighlightUtil.formatType(myTypeElement.getType()));
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return JavaQuickFixBundle.message("delete.catch.family");
  }

  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, final PsiFile file) {
    return myTypeElement.isValid() && PsiManager.getInstance(project).isInProject(myTypeElement.getContainingFile());
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(myTypeElement.getContainingFile())) return;

    final PsiElement parentType = myTypeElement.getParent();
    if (!(parentType instanceof PsiTypeElement)) return;

    final PsiElement first;
    final PsiElement last;
    final PsiElement right = PsiTreeUtil.skipSiblingsForward(myTypeElement, PsiWhiteSpace.class, PsiComment.class);
    if (right instanceof PsiJavaToken && ((PsiJavaToken)right).getTokenType() == JavaTokenType.OR) {
      first = myTypeElement;
      last = right;
    }
    else if (right == null) {
      final PsiElement left = PsiTreeUtil.skipSiblingsBackward(myTypeElement, PsiWhiteSpace.class, PsiComment.class);
      if (!(left instanceof PsiJavaToken)) return;
      final IElementType leftType = ((PsiJavaToken)left).getTokenType();
      if (leftType != JavaTokenType.OR) return;
      first = left;
      last = myTypeElement;
    }
    else {
      return;
    }

    parentType.deleteChildRange(first, last);

    final List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(parentType, PsiTypeElement.class);
    if (typeElements.size() == 1) {
      final PsiElement parameter = parentType.getParent();
      parameter.addRangeAfter(parentType.getFirstChild(), parentType.getLastChild(), parentType);
      parentType.delete();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
