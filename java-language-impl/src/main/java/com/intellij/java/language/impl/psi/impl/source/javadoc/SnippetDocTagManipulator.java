// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiSnippetDocTag;
import consulo.document.util.TextRange;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Contract;

import java.util.List;

public final class SnippetDocTagManipulator extends AbstractElementManipulator<PsiSnippetDocTagImpl> {

  @Override
  public PsiSnippetDocTagImpl handleContentChange(@Nonnull PsiSnippetDocTagImpl element,
                                                  @Nonnull TextRange range,
                                                  String newContent) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());

    final JavaCodeStyleSettingsFacade codeStyleFacade = JavaCodeStyleSettingsFacade.getInstance(element.getProject());
    final String newSnippetTagContent = codeStyleFacade.isJavaDocLeadingAsterisksEnabled()
      ? prependAbsentAsterisks(newContent)
      : newContent;

    final PsiDocComment text = factory.createDocCommentFromText("/**\n" + newSnippetTagContent + "\n*/");
    final PsiSnippetDocTag snippet = PsiTreeUtil.findChildOfType(text, PsiSnippetDocTag.class);
    if (snippet == null) {
      return element;
    }
    return (PsiSnippetDocTagImpl)element.replace(snippet);
  }

  @Contract(pure = true)
  private static @Nonnull
  String prependAbsentAsterisks(@Nonnull String input) {
    return input.replaceAll("(\\n\\s*)([^*\\s])", "$1 * $2");
  }

  @Override
  public @Nonnull
  TextRange getRangeInElement(@Nonnull PsiSnippetDocTagImpl element) {
    final List<TextRange> ranges = element.getContentRanges();
    if (ranges.isEmpty()) return TextRange.EMPTY_RANGE;
    final int startOffset = ranges.get(0).getStartOffset();
    final int endOffset = ContainerUtil.getLastItem(ranges).getEndOffset();
    return TextRange.create(startOffset, endOffset);
  }

  @Nonnull
  @Override
  public Class<PsiSnippetDocTagImpl> getElementClass() {
    return PsiSnippetDocTagImpl.class;
  }
}
