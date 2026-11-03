// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.javadoc.PsiMarkdownCodeBlock;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.LiteralTextEscaper;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiLanguageInjectionHost;
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser;
import org.jspecify.annotations.Nullable;

public class PsiMarkdownCodeBlockImpl extends CompositePsiElement implements PsiMarkdownCodeBlock, PsiLanguageInjectionHost {
  public PsiMarkdownCodeBlockImpl() {
    super(JavaDocElementType.DOC_MARKDOWN_CODE_BLOCK);
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMarkdownCodeBlock(this);
      return;
    }
    super.accept(visitor);
  }

  @Override
  public String toString() {
    return "PsiMarkdownCodeBlock:";
  }

  @Override
  public @Nullable Language getCodeLanguage() {
    String languageInfo = getLanguageInfo();
    if (languageInfo == null) {
      return getLanguage();
    }
    return CodeFenceLanguageGuesser.guessLanguageForInjection(languageInfo);
  }

  @Override
  public String getCodeText() {
    StringBuilder builder = new StringBuilder();

    ASTNode child = getFirstChildNode().getTreeNext();
    if (hasLanguageInfo() && child != null) {
      child = child.getTreeNext();
    }

    while (child != null) {
      IElementType i = child.getElementType();
      if (i != JavaDocTokenType.DOC_CODE_FENCE &&
          i != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS &&
          i != JavaDocTokenType.DOC_INLINE_CODE_FENCE) {
        builder.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return builder.toString();
  }

  @Override
  public boolean isInline() {
    return getFirstChildNode().getElementType() == JavaDocTokenType.DOC_INLINE_CODE_FENCE;
  }

  /**
   * @return The language info string of the fence, for instance {@code java}, or {@code null} for a block using the language of the
   * comment
   */
  public @Nullable String getCodeLanguageInfo() {
    return getLanguageInfo();
  }

  @Override
  public boolean isValidHost() {
    return !isInline();
  }

  @Override
  public PsiLanguageInjectionHost updateText(String text) {
    return ElementManipulators.handleContentChange(this, text);
  }

  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return LiteralTextEscaper.createSimple(this);
  }

  private @Nullable String getLanguageInfo() {
    if (!hasLanguageInfo()) {
      return null;
    }
    return getChildren()[1].getText().trim();
  }

  /**
   * @return True if the block has language info. It doesn't always translate to a {@link Language}
   */
  private boolean hasLanguageInfo() {
    if (isInline()) {
      return false;
    }

    PsiElement[] children = getChildren();
    if (children.length < 2) {
      return false;
    }
    // If the code block is written on only one line (why), there is no language info as well
    if (findChildByType(TokenSet.WHITE_SPACE) == null) {
      return false;
    }

    PsiElement languageInfoChild = children[1];
    if (languageInfoChild.getNode().getElementType() != JavaDocTokenType.DOC_COMMENT_DATA) {
      return false;
    }
    return !languageInfoChild.getText().trim().isEmpty();
  }
}
