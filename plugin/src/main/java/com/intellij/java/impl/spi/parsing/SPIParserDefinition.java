/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.spi.parsing;

import com.intellij.java.language.impl.spi.parsing.SPIElementTypes;
import com.intellij.java.language.impl.spi.parsing.SPILexer;
import com.intellij.java.language.impl.spi.parsing.SPITokenType;
import com.intellij.java.language.impl.spi.psi.SPIFile;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.spi.SPILanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiParser;
import consulo.language.lexer.Lexer;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.ast.TokenType;
import consulo.language.ast.IElementType;
import consulo.language.ast.IFileElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiUtilCore;
import com.intellij.java.impl.spi.psi.SPIClassProviderReferenceElement;
import com.intellij.java.impl.spi.psi.SPIClassProvidersElementList;
import com.intellij.java.impl.spi.psi.SPIPackageOrClassReferenceElement;
import consulo.language.version.LanguageVersion;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

/**
 * User: anna
 */
@ExtensionImpl
public class SPIParserDefinition implements ParserDefinition {
  public static final IFileElementType SPI_FILE_ELEMENT_TYPE = new IFileElementType(SPILanguage.INSTANCE);

  private static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
  private static final Logger LOG = Logger.getInstance(SPIParserDefinition.class);

  @Nonnull
  @Override
  public Language getLanguage() {
    return SPILanguage.INSTANCE;
  }

  @Nonnull
  @Override
  public Lexer createLexer(LanguageVersion languageVersion) {
    return new SPILexer();
  }

  @Override
  public PsiParser createParser(LanguageVersion languageVersion) {
    return new PsiParser() {
      @Nonnull
      @Override
      public ASTNode parse(IElementType root, PsiBuilder builder, LanguageVersion languageVersion) {
        PsiBuilder.Marker rootMarker = builder.mark();
        PsiBuilder.Marker propertiesList = builder.mark();
        while (!builder.eof()) {
          parseProvider(builder);
        }
        propertiesList.done(SPIElementTypes.PROVIDERS_LIST);
        rootMarker.done(root);
        return builder.getTreeBuilt();
      }
    };
  }

  @Override
  public IFileElementType getFileNodeType() {
    return SPI_FILE_ELEMENT_TYPE;
  }

  @Nonnull
  @Override
  public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
    return WHITE_SPACES;
  }

  @Nonnull
  @Override
  public TokenSet getCommentTokens(LanguageVersion languageVersion) {
    return TokenSet.create(JavaTokenType.END_OF_LINE_COMMENT);
  }

  @Nonnull
  @Override
  public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
    return TokenSet.EMPTY;
  }

  @Nonnull
  @Override
  public PsiElement createElement(ASTNode node) {
    IElementType elementType = node.getElementType();
    if (elementType == SPIElementTypes.PROVIDERS_LIST) {
      return new SPIClassProvidersElementList(node);
    }
    if (elementType == SPIElementTypes.PROVIDER) {
      return new SPIClassProviderReferenceElement(node);
    }
    if (elementType == SPIElementTypes.PACK) {
      return new SPIPackageOrClassReferenceElement(node);
    }
    return PsiUtilCore.NULL_PSI_ELEMENT;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new SPIFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }

  public static void parseProvider(PsiBuilder builder) {
    if (builder.getTokenType() == SPITokenType.IDENTIFIER) {
      PsiBuilder.Marker prop = builder.mark();

      parseProviderChar(builder, builder.mark());
      prop.done(SPIElementTypes.PROVIDER);
    }
    else {
      builder.advanceLexer();
      builder.error("Unexpected token");
    }
  }

  private static void parseProviderChar(PsiBuilder builder, PsiBuilder.Marker pack) {
    builder.advanceLexer();
    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.DOT || tokenType == SPITokenType.DOLLAR) {
      pack.done(SPIElementTypes.PACK);
      builder.advanceLexer();
      IElementType initialTokenType = builder.getTokenType();
      if (initialTokenType == null) return;
      parseProviderChar(builder, pack.precede());
    } else {
      pack.drop();
    }
  }
}
