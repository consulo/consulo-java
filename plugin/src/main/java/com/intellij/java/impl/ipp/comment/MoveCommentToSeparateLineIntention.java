/*
 * Copyright 2003-2005 Dave Griffith
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
package com.intellij.java.impl.ipp.comment;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

public class MoveCommentToSeparateLineIntention extends Intention {

  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new CommentOnLineWithSourcePredicate();
  }

  public void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiComment selectedComment = (PsiComment)element;
    PsiElement elementToCheck = selectedComment;
    final PsiWhiteSpace whiteSpace;
    while (true) {
      elementToCheck = PsiTreeUtil.prevLeaf(elementToCheck);
      if (elementToCheck == null) {
        return;
      }
      if (isLineBreakWhiteSpace(elementToCheck)) {
        whiteSpace = (PsiWhiteSpace)elementToCheck;
        break;
      }
    }
    final PsiElement copyWhiteSpace = whiteSpace.copy();
    final PsiElement parent = whiteSpace.getParent();
    assert parent != null;
    final PsiManager manager = selectedComment.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final String commentText = selectedComment.getText();
    final PsiComment newComment =
      factory.createCommentFromText(commentText, parent);
    final PsiElement insertedComment = parent
      .addBefore(newComment, whiteSpace);
    parent.addBefore(copyWhiteSpace, insertedComment);

    selectedComment.delete();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    codeStyleManager.reformat(insertedComment);
  }

  private static boolean isLineBreakWhiteSpace(PsiElement element) {
    if (!(element instanceof PsiWhiteSpace)) {
      return false;
    }
    final String text = element.getText();
    return containsLineBreak(text);
  }

  private static boolean containsLineBreak(String text) {
    return text.indexOf((int)'\n') >= 0 || text.indexOf((int)'\r') >= 0;
  }
}
