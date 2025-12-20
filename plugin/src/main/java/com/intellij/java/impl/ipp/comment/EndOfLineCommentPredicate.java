/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.ast.IElementType;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

class EndOfLineCommentPredicate implements PsiElementPredicate {

  private static final Pattern NO_INSPECTION_PATTERN =
    Pattern.compile("//[\t ]*noinspection .*");

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    if (element instanceof PsiDocComment) {
      return false;
    }
    PsiComment comment = (PsiComment)element;
    IElementType type = comment.getTokenType();
    if (!JavaTokenType.END_OF_LINE_COMMENT.equals(type)) {
      return false;
    }
    String text = comment.getText();
    Matcher matcher = NO_INSPECTION_PATTERN.matcher(text);
    return !matcher.matches();
  }
}