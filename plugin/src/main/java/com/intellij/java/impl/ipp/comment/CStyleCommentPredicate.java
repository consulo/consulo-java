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

import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class CStyleCommentPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    if (element instanceof PsiDocComment) {
      return false;
    }
    final PsiComment comment = (PsiComment)element;
    final IElementType type = comment.getTokenType();
    if (!JavaTokenType.C_STYLE_COMMENT.equals(type)) {
      return false;
    }
    final PsiElement sibling = PsiTreeUtil.nextLeaf(comment);
    if (!(sibling instanceof PsiWhiteSpace)) {
      return false;
    }
    final String whitespaceText = sibling.getText();
    return whitespaceText.indexOf((int)'\n') >= 0 ||
           whitespaceText.indexOf((int)'\r') >= 0;
  }
}
