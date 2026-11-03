// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.lexer;

import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.lexer.Lexer;
import consulo.language.lexer.MergeFunction;
import consulo.language.lexer.MergingLexerAdapterBase;

/**
 * Lexer to wrap around the Java Lexer when parsing Java types for the markdown JavaDoc (JEP-467)
 * In reference links, array types are escaped:  "char\[\]" which the JavaLexer doesn't like
 * <p>
 * It does this by <em>covering up</em> a <i>BAD_CHARACTER</i> token if followed by <i>[</i> or <i>]</i>
 */
public class JavaTypeEscapeLexer extends MergingLexerAdapterBase {
  private static final MergeFunction ESCAPE_MARKDOWN_FUNCTION = (tokenType, lexer) -> {
    if (tokenType != TokenType.BAD_CHARACTER) {
      return tokenType;
    }

    IElementType originalTokenType = lexer.getTokenType();
    if (originalTokenType != JavaTokenType.LBRACKET && originalTokenType != JavaTokenType.RBRACKET) {
      return tokenType;
    }

    lexer.advance();
    return originalTokenType;
  };

  public JavaTypeEscapeLexer(Lexer original) {
    super(original);
  }

  @Override
  public MergeFunction getMergeFunction() {
    return ESCAPE_MARKDOWN_FUNCTION;
  }
}
