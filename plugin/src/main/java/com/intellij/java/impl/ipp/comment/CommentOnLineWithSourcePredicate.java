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

import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.ast.IElementType;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;

class CommentOnLineWithSourcePredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    //final PsiFile file = element.getContainingFile();
    //if (file instanceof JspFile){
    //    return false;
    //}
    if (!(element instanceof PsiComment)) {
      return false;
    }
    if (element instanceof PsiDocComment) {
      return false;
    }

    final PsiComment comment = (PsiComment)element;
    if (comment instanceof PsiLanguageInjectionHost && InjectedLanguageManager.getInstance(comment.getProject()).getInjectedPsiFiles(comment) != null) {
      return false;
    }
    final IElementType type = comment.getTokenType();
    if (!JavaTokenType.C_STYLE_COMMENT.equals(type) &&
        !JavaTokenType.END_OF_LINE_COMMENT.equals(type)) {
      return false; // can't move JSP comments
    }
    final PsiElement prevSibling = PsiTreeUtil.prevLeaf(element);
    if (!(prevSibling instanceof PsiWhiteSpace)) {
      return true;
    }
    final String prevSiblingText = prevSibling.getText();
    if (prevSiblingText.indexOf((int)'\n') < 0 &&
        prevSiblingText.indexOf((int)'\r') < 0) {
      return true;
    }
    final PsiElement nextSibling = PsiTreeUtil.nextLeaf(element);
    if (!(nextSibling instanceof PsiWhiteSpace)) {
      return true;
    }
    final String nextSiblingText = nextSibling.getText();
    return nextSiblingText.indexOf((int)'\n') < 0 &&
           nextSiblingText.indexOf((int)'\r') < 0;
  }
}
