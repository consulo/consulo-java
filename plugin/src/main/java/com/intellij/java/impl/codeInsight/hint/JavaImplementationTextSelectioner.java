/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 01-Feb-2008
 */
package com.intellij.java.impl.codeInsight.hint;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.ImplementationTextSelectioner;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaImplementationTextSelectioner implements ImplementationTextSelectioner {
  private static final Logger LOG = Logger.getInstance(JavaImplementationTextSelectioner.class);

  @Override
  public int getTextStartOffset(@Nonnull final PsiElement parent) {
    PsiElement element = parent;
    if (element instanceof PsiDocCommentOwner) {
      PsiDocComment comment = ((PsiDocCommentOwner) element).getDocComment();
      if (comment != null) {
        element = comment.getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
      }
    }

    if (element != null) {
      TextRange range = element.getTextRange();
      if (range != null) {
        return range.getStartOffset();
      }
      LOG.error("Range should not be null: " + element + "; " + element.getClass());
    }

    LOG.error("Element should not be null: " + parent.getText());
    return parent.getTextRange().getStartOffset();
  }

  @Override
  public int getTextEndOffset(@Nonnull PsiElement element) {
    return element.getTextRange().getEndOffset();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}