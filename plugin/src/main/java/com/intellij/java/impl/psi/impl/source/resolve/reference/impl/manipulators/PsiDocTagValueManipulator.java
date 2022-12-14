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

package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.manipulators;

import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.psi.AbstractElementManipulator;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;

/**
 * @author Gregory.Shrago
 */
@ExtensionImpl
public class PsiDocTagValueManipulator extends AbstractElementManipulator<PsiDocTag> {

  @Override
  public PsiDocTag handleContentChange(PsiDocTag tag, TextRange range, String newContent) throws IncorrectOperationException {
    final StringBuilder replacement = new StringBuilder( tag.getText() );

    replacement.replace(
      range.getStartOffset(),
      range.getEndOffset(),
      newContent
    );
    return (PsiDocTag)tag.replace(JavaPsiFacade.getInstance(tag.getProject()).getElementFactory().createDocTagFromText(replacement.toString()));
  }

  @Override
  public TextRange getRangeInElement(final PsiDocTag tag) {
    final PsiElement[] elements = tag.getDataElements();
    if (elements.length == 0) {
      final PsiElement name = tag.getNameElement();
      final int offset = name.getStartOffsetInParent() + name.getTextLength();
      return new TextRange(offset, offset);
    }
    final PsiElement first = elements[0];
    final PsiElement last = elements[elements.length - 1];
    return new TextRange(first.getStartOffsetInParent(), last.getStartOffsetInParent()+last.getTextLength());
  }

  @Nonnull
  @Override
  public Class<PsiDocTag> getElementClass() {
    return PsiDocTag.class;
  }
}