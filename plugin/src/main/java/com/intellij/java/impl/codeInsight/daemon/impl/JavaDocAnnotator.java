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
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.analysis.impl.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;

import javax.annotation.Nonnull;

/**
 * @author Dmitry Avdeev
 *         Date: 11/8/11
 */
public class JavaDocAnnotator implements Annotator {
  @Override
  public void annotate(@Nonnull PsiElement element, @Nonnull AnnotationHolder holder) {
    if (element instanceof PsiDocTag) {
      String name = ((PsiDocTag)element).getName();
      if ("param".equals(name)) {
        PsiDocTagValue tagValue = ((PsiDocTag)element).getValueElement();
        if (tagValue != null) {
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(tagValue).textAttributes(JavaHighlightingColors.DOC_COMMENT_TAG_VALUE).create();
        }
      }
    }
  }
}
