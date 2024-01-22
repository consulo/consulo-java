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
package com.intellij.java.language.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaFileImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.psi.JavaLanguageVersion;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.IFileElementType;
import consulo.language.ast.TokenSet;
import consulo.language.file.FileViewProvider;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.LanguageUtil;
import consulo.language.version.LanguageVersion;

import jakarta.annotation.Nonnull;

/**
 * @author max
 */
@ExtensionImpl
public class JavaParserDefinition implements ParserDefinition {
  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  @Nonnull
  public Lexer createLexer(@Nonnull LanguageVersion languageVersion) {
    JavaLanguageVersion wrapper = (JavaLanguageVersion) languageVersion;
    return new JavaLexer(wrapper.getLanguageLevel());
  }

  @Nonnull
  @Override
  public IFileElementType getFileNodeType() {
    return JavaStubElementTypes.JAVA_FILE;
  }

  @Override
  @Nonnull
  public TokenSet getWhitespaceTokens(@Nonnull LanguageVersion languageVersion) {
    return ElementType.JAVA_WHITESPACE_BIT_SET;
  }

  @Override
  @Nonnull
  public TokenSet getCommentTokens(@Nonnull LanguageVersion languageVersion) {
    return ElementType.JAVA_COMMENT_BIT_SET;
  }

  @Override
  @Nonnull
  public TokenSet getStringLiteralElements(@Nonnull LanguageVersion languageVersion) {
    return TokenSet.create(JavaElementType.LITERAL_EXPRESSION);
  }

  @Override
  @Nonnull
  public PsiParser createParser(@Nonnull LanguageVersion languageVersion) {
    throw new UnsupportedOperationException();
  }

  @RequiredReadAction
  @Override
  @Nonnull
  public PsiElement createElement(@Nonnull final ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof JavaStubElementType) {
      return ((JavaStubElementType) type).createPsi(node);
    }

    throw new IllegalStateException("Incorrect node for JavaParserDefinition: " + node + " (" + type + ")");
  }

  @Override
  public PsiFile createFile(@Nonnull final FileViewProvider viewProvider) {
    return new PsiJavaFileImpl(viewProvider);
  }

  @Nonnull
  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final PsiFile containingFile = left.getTreeParent().getPsi().getContainingFile();
    final Lexer lexer;
    if (containingFile instanceof PsiJavaFile) {
      lexer = new JavaLexer(((PsiJavaFile) containingFile).getLanguageLevel());
    } else {
      lexer = new JavaLexer(LanguageLevel.HIGHEST);
    }
    if (right.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      return SpaceRequirements.MUST_NOT;
    }
    if (left.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      return SpaceRequirements.MUST_NOT;
    }
    final SpaceRequirements spaceRequirements = LanguageUtil.canStickTokensTogetherByLexer(left, right, lexer);
    if (left.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }

    if (left.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA) {
      String text = left.getText();
      if (text.length() > 0 && Character.isWhitespace(text.charAt(text.length() - 1))) {
        return SpaceRequirements.MAY;
      }
    }

    if (right.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA) {
      String text = right.getText();
      if (text.length() > 0 && Character.isWhitespace(text.charAt(0))) {
        return SpaceRequirements.MAY;
      }
    } else if (right.getElementType() == JavaDocTokenType.DOC_INLINE_TAG_END) {
      return SpaceRequirements.MAY;
    }

    return spaceRequirements;
  }
}
