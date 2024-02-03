// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.DumbAware;
import consulo.language.editor.rawHighlight.HighlightInfoHolder;
import consulo.language.editor.rawHighlight.HighlightVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;

public class JavaNamesHighlightVisitor extends JavaElementVisitor implements HighlightVisitor, DumbAware {
  private HighlightInfoHolder myHolder;
  private PsiFile myFile;
  private LanguageLevel myLanguageLevel;
  private boolean shouldHighlightSoftKeywords;

  @Override
  public void visit(@Nonnull PsiElement element) {
    element.accept(this);
  }

  @Override
  public boolean analyze(@Nonnull PsiFile file, boolean updateWholeFile, @Nonnull HighlightInfoHolder holder, @Nonnull Runnable highlight) {
    try {
      prepare(holder, file);
      highlight.run();
    }
    finally {
      myFile = null;
      myHolder = null;
    }

    return true;
  }

  @RequiredReadAction
  private void prepare(@Nonnull HighlightInfoHolder holder, @Nonnull PsiFile file) {
    myHolder = holder;
    myFile = file;
    myLanguageLevel = PsiUtil.getLanguageLevel(file);
    shouldHighlightSoftKeywords = PsiJavaModule.MODULE_INFO_FILE.equals(file.getName()) || myLanguageLevel.isAtLeast(LanguageLevel.JDK_10);
  }

  @Override
  public void visitKeyword(@Nonnull PsiKeyword keyword) {
    if (shouldHighlightSoftKeywords &&
      (JavaLexer.isSoftKeyword(keyword.getNode().getChars(),
                               myLanguageLevel) || JavaTokenType.NON_SEALED_KEYWORD == keyword.getTokenType())) {
      myHolder.add(HighlightNamesUtil.highlightKeyword(keyword));
    }
  }
}
