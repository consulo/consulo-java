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
package com.intellij.java.impl.codeInsight.generation.surroundWith;

import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.editor.surroundWith.SurroundWithRangeAdjuster;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.xml.lang.html.HTMLLanguage;
import consulo.xml.lang.xhtml.XHTMLLanguage;
import consulo.xml.lang.xml.XMLLanguage;

/**
 * @author yole
 */
@ExtensionImpl
public class XmlSurroundWithRangeAdjuster implements SurroundWithRangeAdjuster {
  private static boolean isLanguageWithWSSignificant(Language lang) {
    return lang == HTMLLanguage.INSTANCE ||
        lang == XHTMLLanguage.INSTANCE/* ||
           lang == StdLanguages.JSP ||
           lang == StdLanguages.JSPX*/;
  }

  private static Language getLanguage(PsiElement element) {
    Language lang = element.getLanguage();
    if (lang == XMLLanguage.INSTANCE) {
      PsiElement parent = element.getParent();
      lang = parent.getLanguage();
    }
    return lang;
  }

  @Override
  public TextRange adjustSurroundWithRange(final PsiFile file, final TextRange selectedRange) {
    int startOffset = selectedRange.getStartOffset();
    int endOffset = selectedRange.getEndOffset();
    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);

    Language lang1 = getLanguage(element1);
    Language lang2 = getLanguage(element2);

    if (element1 instanceof PsiWhiteSpace && isLanguageWithWSSignificant(lang1)) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace && isLanguageWithWSSignificant(lang2)) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset);
    }

    lang1 = getLanguage(element1);
    lang2 = getLanguage(element2);

    if (lang1 != lang2) return null;
    return new TextRange(startOffset, endOffset);
  }
}
