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

package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers;

import consulo.document.util.TextRange;
import consulo.util.lang.CharFilter;
import consulo.util.lang.StringUtil;
import consulo.language.psi.ElementManipulator;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProviderBase;
import consulo.language.psi.path.FileReferenceSet;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.ProcessingContext;

import javax.annotation.Nonnull;

/**
 * @author davdeev
 */
public class PathListReferenceProvider extends PsiReferenceProviderBase {

  @Override
  @Nonnull
  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull final ProcessingContext context) {
    return getReferencesByElement(element);
  }

  protected boolean disableNonSlashedPaths() {
    return true;
  }

  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element) {

    PsiReference[] result = PsiReference.EMPTY_ARRAY;
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null;
    final TextRange range = manipulator.getRangeInElement(element);
    String s = range.substring(element.getText());
    int offset = range.getStartOffset();
    if (disableNonSlashedPaths() && !s.trim().startsWith("/")) {
      return result;
    }
    int pos = -1;
    char separator = getSeparator();
    while (true) {
      int nextPos = s.indexOf(separator, pos + 1);
      if (nextPos == -1) {
        PsiReference[] refs =
            createReferences(element, s.substring(pos + 1), pos + offset + 1, false);
        result = ArrayUtil.mergeArrays(result, refs);
        break;
      } else {
        PsiReference[] refs =
            createReferences(element, s.substring(pos + 1, nextPos), pos + offset + 1, false);
        result = ArrayUtil.mergeArrays(result, refs);
        pos = nextPos;
      }
    }

    return result;
  }

  protected char getSeparator() {
    return ',';
  }

  protected PsiReference[] createReferences(PsiElement element, String s, int offset, final boolean soft) {
    int contentOffset = StringUtil.findFirst(s, CharFilter.NOT_WHITESPACE_FILTER);
    if (contentOffset >= 0) {
      offset += contentOffset;
    }
    return new FileReferenceSet(s.trim(), element, offset, this, true) {

      @Override
      protected boolean isSoft() {
        return soft;
      }
    }.getAllReferences();
  }
}
