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
package com.intellij.java.impl.ide.highlighter;

import com.intellij.java.analysis.impl.ide.highlighter.JavaHighlightingColors;
import com.intellij.java.impl.lexer.JavaHighlightingLexer;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.codeEditor.HighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.java.language.psi.JavaLanguageVersion;
import consulo.language.ast.IElementType;
import consulo.language.ast.StringEscapesTokenTypes;
import consulo.language.ast.TokenType;
import consulo.language.editor.highlight.LanguageVersionableSyntaxHighlighter;
import consulo.language.lexer.Lexer;
import consulo.language.version.LanguageVersion;
import consulo.xml.psi.xml.XmlTokenType;

import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class JavaFileHighlighter extends LanguageVersionableSyntaxHighlighter {
  private static final Map<IElementType, TextAttributesKey> ourMap1;
  private static final Map<IElementType, TextAttributesKey> ourMap2;

  static {
    ourMap1 = new HashMap<>();
    ourMap2 = new HashMap<>();

    fillMap(ourMap1, ElementType.KEYWORD_BIT_SET, JavaHighlightingColors.KEYWORD);
    fillMap(ourMap1, ElementType.LITERAL_BIT_SET, JavaHighlightingColors.KEYWORD);
    fillMap(ourMap1, ElementType.OPERATION_BIT_SET, JavaHighlightingColors.OPERATION_SIGN);

    for (IElementType type : JavaDocTokenType.ALL_JAVADOC_TOKENS.getTypes()) {
      ourMap1.put(type, JavaHighlightingColors.DOC_COMMENT);
    }

    ourMap1.put(XmlTokenType.XML_DATA_CHARACTERS, JavaHighlightingColors.DOC_COMMENT);
    ourMap1.put(XmlTokenType.XML_REAL_WHITE_SPACE, JavaHighlightingColors.DOC_COMMENT);
    ourMap1.put(XmlTokenType.TAG_WHITE_SPACE, JavaHighlightingColors.DOC_COMMENT);

    ourMap1.put(JavaTokenType.INTEGER_LITERAL, JavaHighlightingColors.NUMBER);
    ourMap1.put(JavaTokenType.LONG_LITERAL, JavaHighlightingColors.NUMBER);
    ourMap1.put(JavaTokenType.FLOAT_LITERAL, JavaHighlightingColors.NUMBER);
    ourMap1.put(JavaTokenType.DOUBLE_LITERAL, JavaHighlightingColors.NUMBER);
    ourMap1.put(JavaTokenType.STRING_LITERAL, JavaHighlightingColors.STRING);
    ourMap1.put(JavaTokenType.TEXT_BLOCK_LITERAL, JavaHighlightingColors.STRING);
    ourMap1.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, JavaHighlightingColors.VALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, JavaHighlightingColors.INVALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, JavaHighlightingColors.INVALID_STRING_ESCAPE);
    ourMap1.put(JavaTokenType.CHARACTER_LITERAL, JavaHighlightingColors.STRING);

    ourMap1.put(JavaTokenType.LPARENTH, JavaHighlightingColors.PARENTHESES);
    ourMap1.put(JavaTokenType.RPARENTH, JavaHighlightingColors.PARENTHESES);

    ourMap1.put(JavaTokenType.LBRACE, JavaHighlightingColors.BRACES);
    ourMap1.put(JavaTokenType.RBRACE, JavaHighlightingColors.BRACES);

    ourMap1.put(JavaTokenType.LBRACKET, JavaHighlightingColors.BRACKETS);
    ourMap1.put(JavaTokenType.RBRACKET, JavaHighlightingColors.BRACKETS);

    ourMap1.put(JavaTokenType.COMMA, JavaHighlightingColors.COMMA);
    ourMap1.put(JavaTokenType.DOT, JavaHighlightingColors.DOT);
    ourMap1.put(JavaTokenType.SEMICOLON, JavaHighlightingColors.JAVA_SEMICOLON);

    ourMap1.put(JavaTokenType.C_STYLE_COMMENT, JavaHighlightingColors.JAVA_BLOCK_COMMENT);
    ourMap1.put(JavaDocElementType.DOC_COMMENT, JavaHighlightingColors.DOC_COMMENT);
    ourMap1.put(JavaTokenType.END_OF_LINE_COMMENT, JavaHighlightingColors.LINE_COMMENT);
    ourMap1.put(TokenType.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    ourMap1.put(JavaDocTokenType.DOC_TAG_NAME, JavaHighlightingColors.DOC_COMMENT);
    ourMap2.put(JavaDocTokenType.DOC_TAG_NAME, JavaHighlightingColors.DOC_COMMENT_TAG);

    IElementType[] javaDocMarkup = {
        XmlTokenType.XML_START_TAG_START,
        XmlTokenType.XML_END_TAG_START,
        XmlTokenType.XML_TAG_END,
        XmlTokenType.XML_EMPTY_ELEMENT_END,
        XmlTokenType.TAG_WHITE_SPACE,
        XmlTokenType.XML_TAG_NAME,
        XmlTokenType.XML_NAME,
        XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
        XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER,
        XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,
        XmlTokenType.XML_CHAR_ENTITY_REF,
        XmlTokenType.XML_EQ
    };
    for (IElementType idx : javaDocMarkup) {
      ourMap1.put(idx, JavaHighlightingColors.DOC_COMMENT);
      ourMap2.put(idx, JavaHighlightingColors.DOC_COMMENT_MARKUP);
    }
  }

  public JavaFileHighlighter() {
    this(LanguageLevel.HIGHEST.toLangVersion());
  }

  public JavaFileHighlighter(@Nonnull LanguageVersion languageLevel) {
    super(languageLevel);
  }

  @Override
  @Nonnull
  public Lexer getHighlightingLexer(LanguageVersion languageVersion) {
    return new JavaHighlightingLexer(((JavaLanguageVersion) languageVersion).getLanguageLevel());
  }

  @Override
  @Nonnull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ourMap1.get(tokenType), ourMap2.get(tokenType));
  }
}