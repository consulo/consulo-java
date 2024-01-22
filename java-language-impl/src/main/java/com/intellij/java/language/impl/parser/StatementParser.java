// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.parser;

import com.intellij.java.language.JavaPsiBundle;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.language.ast.IElementType;
import consulo.language.ast.ILazyParseableElementType;
import consulo.language.ast.TokenSet;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.WhitespacesBinders;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.Contract;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

import static com.intellij.java.language.impl.parser.JavaParserUtil.*;
import static consulo.language.parser.PsiBuilderUtil.*;

public class StatementParser {
  private enum BraceMode {
    TILL_FIRST,
    TILL_LAST
  }

  private static final TokenSet YIELD_STMT_INDICATOR_TOKENS = TokenSet.create(
    JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.EXCL,

    JavaTokenType.SUPER_KEYWORD, JavaTokenType.THIS_KEYWORD,

    JavaTokenType.TRUE_KEYWORD, JavaTokenType.FALSE_KEYWORD, JavaTokenType.NULL_KEYWORD,

    JavaTokenType.STRING_LITERAL, JavaTokenType.INTEGER_LITERAL, JavaTokenType.DOUBLE_LITERAL,
    JavaTokenType.FLOAT_LITERAL, JavaTokenType.LONG_LITERAL, JavaTokenType.CHARACTER_LITERAL,

    JavaTokenType.IDENTIFIER, JavaTokenType.SWITCH_KEYWORD, JavaTokenType.NEW_KEYWORD,

    JavaTokenType.LPARENTH,

    // recovery
    JavaTokenType.RBRACE, JavaTokenType.SEMICOLON, JavaTokenType.CASE_KEYWORD
  );

  private static final TokenSet TRY_CLOSERS_SET = TokenSet.create(JavaTokenType.CATCH_KEYWORD, JavaTokenType.FINALLY_KEYWORD);

  private final JavaParser myParser;

  public StatementParser(@Nonnull JavaParser javaParser) {
    myParser = javaParser;
  }

  @jakarta.annotation.Nullable
  public PsiBuilder.Marker parseCodeBlock(@jakarta.annotation.Nonnull PsiBuilder builder) {
    return parseCodeBlock(builder, false);
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlock(@Nonnull PsiBuilder builder, boolean isStatement) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) {
      return null;
    }
    if (isStatement && isParseStatementCodeBlocksDeep(builder)) {
      return parseCodeBlockDeep(builder, false);
    }
    return parseBlockLazy(builder, JavaTokenType.LBRACE, JavaTokenType.RBRACE, JavaElementType.CODE_BLOCK);
  }

  /**
   * tries to parse a code block with corresponding left and right braces.
   *
   * @return collapsed marker of the block or `null` if there is no code block at all.
   */
  @Nullable
  public static PsiBuilder.Marker parseBlockLazy(@Nonnull PsiBuilder builder,
                                                 @Nonnull IElementType leftBrace,
                                                 @Nonnull IElementType rightBrace,
                                                 @Nonnull IElementType codeBlock) {
    if (builder.getTokenType() != leftBrace)
      return null;

    PsiBuilder.Marker marker = builder.mark();

    builder.advanceLexer();

    int braceCount = 1;

    while (braceCount > 0 && !builder.eof()) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == leftBrace) {
        braceCount++;
      }
      else if (tokenType == rightBrace) {
        braceCount--;
      }
      builder.advanceLexer();
    }

    marker.collapse(codeBlock);

    if (braceCount > 0) {
      marker.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }

    return marker;
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlockDeep(@Nonnull PsiBuilder builder, boolean parseUntilEof) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) {
      return null;
    }

    PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    parseStatements(builder, parseUntilEof ? BraceMode.TILL_LAST : BraceMode.TILL_FIRST);

    boolean greedyBlock = !expectOrError(builder, JavaTokenType.RBRACE, "expected.rbrace");
    builder.getTokenType(); // eat spaces

    done(codeBlock, JavaElementType.CODE_BLOCK);
    if (greedyBlock) {
      codeBlock.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    return codeBlock;
  }

  public void parseStatements(@Nonnull PsiBuilder builder) {
    parseStatements(builder, null);
  }

  private void parseStatements(PsiBuilder builder, @Nullable BraceMode braceMode) {
    while (builder.getTokenType() != null) {
      PsiBuilder.Marker statement = parseStatement(builder);
      if (statement != null) {
        continue;
      }

      IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.RBRACE &&
        (braceMode == BraceMode.TILL_FIRST || braceMode == BraceMode.TILL_LAST && builder.lookAhead(1) == null)) {
        break;
      }

      PsiBuilder.Marker error = builder.mark();
      builder.advanceLexer();
      if (tokenType == JavaTokenType.ELSE_KEYWORD) {
        error.error(JavaErrorBundle.message("else.without.if"));
      }
      else if (tokenType == JavaTokenType.CATCH_KEYWORD) {
        error.error(JavaErrorBundle.message("catch.without.try"));
      }
      else if (tokenType == JavaTokenType.FINALLY_KEYWORD) {
        error.error(JavaErrorBundle.message("finally.without.try"));
      }
      else {
        error.error(JavaErrorBundle.message("unexpected.token"));
      }
    }
  }

  @jakarta.annotation.Nullable
  public PsiBuilder.Marker parseStatement(@Nonnull PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.IF_KEYWORD) {
      return parseIfStatement(builder);
    }
    else if (tokenType == JavaTokenType.WHILE_KEYWORD) {
      return parseWhileStatement(builder);
    }
    else if (tokenType == JavaTokenType.FOR_KEYWORD) {
      return parseForStatement(builder);
    }
    else if (tokenType == JavaTokenType.DO_KEYWORD) {
      return parseDoWhileStatement(builder);
    }
    else if (tokenType == JavaTokenType.SWITCH_KEYWORD) {
      return parseSwitchStatement(builder);
    }
    else if (tokenType == JavaTokenType.CASE_KEYWORD || tokenType == JavaTokenType.DEFAULT_KEYWORD) {
      return parseSwitchLabelStatement(builder);
    }
    else if (tokenType == JavaTokenType.BREAK_KEYWORD) {
      return parseBreakStatement(builder);
    }
    else if (isStmtYieldToken(builder, tokenType)) {
      return parseYieldStatement(builder);
    }
    else if (tokenType == JavaTokenType.CONTINUE_KEYWORD) {
      return parseContinueStatement(builder);
    }
    else if (tokenType == JavaTokenType.RETURN_KEYWORD) {
      return parseReturnStatement(builder);
    }
    else if (tokenType == JavaTokenType.THROW_KEYWORD) {
      return parseThrowStatement(builder);
    }
    else if (tokenType == JavaTokenType.SYNCHRONIZED_KEYWORD) {
      return parseSynchronizedStatement(builder);
    }
    else if (tokenType == JavaTokenType.TRY_KEYWORD) {
      return parseTryStatement(builder);
    }
    else if (tokenType == JavaTokenType.ASSERT_KEYWORD) {
      return parseAssertStatement(builder);
    }
    else if (tokenType == JavaTokenType.LBRACE) {
      return parseBlockStatement(builder);
    }
    else if (tokenType instanceof ILazyParseableElementType) {
      builder.advanceLexer();
      return null;
    }
    else if (tokenType == JavaTokenType.SEMICOLON) {
      PsiBuilder.Marker empty = builder.mark();
      builder.advanceLexer();
      done(empty, JavaElementType.EMPTY_STATEMENT);
      return empty;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.AT) {
      PsiBuilder.Marker refPos = builder.mark();
      myParser.getDeclarationParser().parseAnnotations(builder);
      skipQualifiedName(builder);
      IElementType suspectedLT = builder.getTokenType(), next = builder.lookAhead(1);
      refPos.rollbackTo();

      if (suspectedLT == JavaTokenType.LT || suspectedLT == JavaTokenType.DOT && next == JavaTokenType.AT) {
        PsiBuilder.Marker declStatement = builder.mark();

        if (myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.CODE_BLOCK) != null) {
          done(declStatement, JavaElementType.DECLARATION_STATEMENT);
          return declStatement;
        }

        ReferenceParser.TypeInfo type = myParser.getReferenceParser().parseTypeInfo(builder, 0);
        if (suspectedLT == JavaTokenType.LT && (type == null || !type.isParameterized)) {
          declStatement.rollbackTo();
        }
        else if (type == null || builder.getTokenType() != JavaTokenType.DOUBLE_COLON) {
          error(builder, JavaPsiBundle.message("expected.identifier"));
          if (type == null)
            builder.advanceLexer();
          done(declStatement, JavaElementType.DECLARATION_STATEMENT);
          return declStatement;
        }
        else {
          declStatement.rollbackTo();  // generic type followed by the double colon is a good candidate for being a constructor reference
        }
      }
    }

    PsiBuilder.Marker pos = builder.mark();
    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);

    if (expr != null) {
      int count = 1;
      PsiBuilder.Marker list = expr.precede();
      PsiBuilder.Marker statement = list.precede();
      while (builder.getTokenType() == JavaTokenType.COMMA) {
        PsiBuilder.Marker commaPos = builder.mark();
        builder.advanceLexer();
        PsiBuilder.Marker expr1 = myParser.getExpressionParser().parse(builder);
        if (expr1 == null) {
          commaPos.rollbackTo();
          break;
        }
        commaPos.drop();
        count++;
      }
      if (count > 1) {
        pos.drop();
        done(list, JavaElementType.EXPRESSION_LIST);
        semicolon(builder);
        done(statement, JavaElementType.EXPRESSION_LIST_STATEMENT);
        return statement;
      }
      if (exprType(expr) != JavaElementType.REFERENCE_EXPRESSION) {
        drop(list, pos);
        semicolon(builder);
        done(statement, JavaElementType.EXPRESSION_STATEMENT);
        return statement;
      }
      pos.rollbackTo();
    }
    else {
      pos.drop();
    }

    PsiBuilder.Marker decl = myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.CODE_BLOCK);
    if (decl != null) {
      PsiBuilder.Marker statement = decl.precede();
      done(statement, JavaElementType.DECLARATION_STATEMENT);
      return statement;
    }

    if (builder.getTokenType() == JavaTokenType.IDENTIFIER && builder.lookAhead(1) == JavaTokenType.COLON) {
      PsiBuilder.Marker statement = builder.mark();
      advance(builder, 2);
      parseStatement(builder);
      done(statement, JavaElementType.LABELED_STATEMENT);
      return statement;
    }

    if (expr != null) {
      PsiBuilder.Marker statement = builder.mark();
      myParser.getExpressionParser().parse(builder);
      semicolon(builder);
      done(statement, JavaElementType.EXPRESSION_STATEMENT);
      return statement;
    }

    return null;
  }

  private static boolean isStmtYieldToken(@Nonnull PsiBuilder builder, IElementType tokenType) {
    if (!(tokenType == JavaTokenType.IDENTIFIER &&
      PsiKeyword.YIELD.equals(builder.getTokenText()) &&
      getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_14))) {
      return false;
    }
    // we prefer to parse it as yield stmt wherever possible (even in incomplete syntax)
    PsiBuilder.Marker maybeYieldStmt = builder.mark();
    builder.advanceLexer();
    IElementType tokenAfterYield = builder.getTokenType();
    if (tokenAfterYield == null || YIELD_STMT_INDICATOR_TOKENS.contains(tokenAfterYield)) {
      maybeYieldStmt.rollbackTo();
      return true;
    }
    if (JavaTokenType.PLUSPLUS.equals(tokenAfterYield) || JavaTokenType.MINUSMINUS.equals(tokenAfterYield)) {
      builder.advanceLexer();
      boolean isYieldStmt = !builder.getTokenType().equals(JavaTokenType.SEMICOLON);
      maybeYieldStmt.rollbackTo();
      return isYieldStmt;
    }
    maybeYieldStmt.rollbackTo();
    return false;
  }

  private static void skipQualifiedName(PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      return;
    }
    while (builder.getTokenType() == JavaTokenType.DOT && builder.lookAhead(1) == JavaTokenType.IDENTIFIER) {
      advance(builder, 2);
    }
  }

  @Nonnull
  private PsiBuilder.Marker parseIfStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (parseExprInParenth(builder)) {
      PsiBuilder.Marker thenStatement = parseStatement(builder);
      if (thenStatement == null) {
        error(builder, JavaErrorBundle.message("expected.statement"));
      }
      else if (expect(builder, JavaTokenType.ELSE_KEYWORD)) {
        PsiBuilder.Marker elseStatement = parseStatement(builder);
        if (elseStatement == null) {
          error(builder, JavaErrorBundle.message("expected.statement"));
        }
      }
    }

    done(statement, JavaElementType.IF_STATEMENT);
    return statement;
  }

  @Nonnull
  private PsiBuilder.Marker parseWhileStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, JavaElementType.WHILE_STATEMENT, false);
  }

  @Nonnull
  private PsiBuilder.Marker parseForStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaPsiBundle.message("expected.lparen"));
      done(statement, JavaElementType.FOR_STATEMENT);
      return statement;
    }

    if (isRecordPatternInForEach(builder)) {
      myParser.getPatternParser().parsePattern(builder);
      if (builder.getTokenType() == JavaTokenType.COLON) {
        return parseForEachFromColon(builder, statement, JavaElementType.FOREACH_PATTERN_STATEMENT);
      }
      error(builder, JavaPsiBundle.message("expected.colon"));
      // recovery: just skip everything until ')'
      while (true) {
        IElementType tokenType = builder.getTokenType();
        if (tokenType == null) {
          done(statement, JavaElementType.FOREACH_PATTERN_STATEMENT);
          return statement;
        }
        if (tokenType == JavaTokenType.RPARENTH) {
          return parserForEachFromRparenth(builder, statement, JavaElementType.FOREACH_PATTERN_STATEMENT);
        }
        builder.advanceLexer();
      }
    }

    PsiBuilder.Marker afterParenth = builder.mark();
    PsiBuilder.Marker param = myParser.getDeclarationParser().parseParameter(builder, false, false, true);
    if (param == null || exprType(param) != JavaElementType.PARAMETER || builder.getTokenType() != JavaTokenType.COLON) {
      afterParenth.rollbackTo();
      return parseForLoopFromInitializer(builder, statement);
    }
    else {
      afterParenth.drop();
      return parseForEachFromColon(builder, statement, JavaElementType.FOREACH_STATEMENT);
    }
  }

  @Contract(pure = true)
  private boolean isRecordPatternInForEach(final PsiBuilder builder) {
    PsiBuilder.Marker patternStart = myParser.getPatternParser().preParsePattern(builder, false);
    if (patternStart == null) {
      return false;
    }
    if (builder.getTokenType() != JavaTokenType.LPARENTH) {
      patternStart.rollbackTo();
      return false;
    }
    builder.advanceLexer();

    // we must distinguish a record pattern from method call in for (foo();;)
    int parenBalance = 1;
    while (true) {
      IElementType current = builder.getTokenType();
      if (current == null) {
        patternStart.rollbackTo();
        return false;
      }
      if (current == JavaTokenType.LPARENTH) {
        parenBalance++;
      }
      if (current == JavaTokenType.RPARENTH) {
        parenBalance--;
        if (parenBalance == 0) {
          break;
        }
      }
      builder.advanceLexer();
    }
    builder.advanceLexer();
    boolean isRecordPattern = builder.getTokenType() != JavaTokenType.SEMICOLON && builder.getTokenType() != JavaTokenType.DOT;
    patternStart.rollbackTo();
    return isRecordPattern;
  }

  @Nonnull
  private PsiBuilder.Marker parseForLoopFromInitializer(PsiBuilder builder, PsiBuilder.Marker statement) {
    if (parseStatement(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.statement"));
      if (!expect(builder, JavaTokenType.RPARENTH)) {
        done(statement, JavaElementType.FOR_STATEMENT);
        return statement;
      }
    }
    else {
      boolean missingSemicolon = false;
      if (getLastToken(builder) != JavaTokenType.SEMICOLON) {
        missingSemicolon = !expectOrError(builder, JavaTokenType.SEMICOLON, "expected.semicolon");
      }

      PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
      missingSemicolon &= expr == null;

      if (!expect(builder, JavaTokenType.SEMICOLON)) {
        if (!missingSemicolon) {
          error(builder, JavaErrorBundle.message("expected.semicolon"));
        }
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          done(statement, JavaElementType.FOR_STATEMENT);
          return statement;
        }
      }
      else {
        parseForUpdateExpressions(builder);
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          error(builder, JavaErrorBundle.message("expected.rparen"));
          done(statement, JavaElementType.FOR_STATEMENT);
          return statement;
        }
      }
    }

    PsiBuilder.Marker bodyStatement = parseStatement(builder);
    if (bodyStatement == null) {
      error(builder, JavaErrorBundle.message("expected.statement"));
    }

    done(statement, JavaElementType.FOR_STATEMENT);
    return statement;
  }

  private static IElementType getLastToken(PsiBuilder builder) {
    IElementType token;
    int offset = -1;
    while (ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains((token = builder.rawLookup(offset)))) {
      offset--;
    }
    return token;
  }

  private void parseForUpdateExpressions(PsiBuilder builder) {
    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null) {
      return;
    }

    PsiBuilder.Marker expressionStatement;
    if (builder.getTokenType() != JavaTokenType.COMMA) {
      expressionStatement = expr.precede();
      done(expressionStatement, JavaElementType.EXPRESSION_STATEMENT);
    }
    else {
      PsiBuilder.Marker expressionList = expr.precede();
      expressionStatement = expressionList.precede();

      do {
        builder.advanceLexer();
        PsiBuilder.Marker nextExpression = myParser.getExpressionParser().parse(builder);
        if (nextExpression == null) {
          error(builder, JavaErrorBundle.message("expected.expression"));
        }
      }
      while (builder.getTokenType() == JavaTokenType.COMMA);

      done(expressionList, JavaElementType.EXPRESSION_LIST);
      done(expressionStatement, JavaElementType.EXPRESSION_LIST_STATEMENT);
    }

    expressionStatement.setCustomEdgeTokenBinders(null, WhitespacesBinders.DEFAULT_RIGHT_BINDER);
  }

  @Nonnull
  private PsiBuilder.Marker parseForEachFromColon(PsiBuilder builder, PsiBuilder.Marker statement, IElementType foreachStatement) {
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
    }

    return parserForEachFromRparenth(builder, statement, foreachStatement);
  }

  private PsiBuilder.Marker parserForEachFromRparenth(PsiBuilder builder, PsiBuilder.Marker statement, IElementType forEachType) {
    if (expectOrError(builder, JavaTokenType.RPARENTH, "expected.rparen") && parseStatement(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.statement"));
    }

    done(statement, forEachType);
    return statement;
  }


  @Nonnull
  private PsiBuilder.Marker parseDoWhileStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    PsiBuilder.Marker body = parseStatement(builder);
    if (body == null) {
      error(builder, JavaErrorBundle.message("expected.statement"));
    }
    else if (!expect(builder, JavaTokenType.WHILE_KEYWORD)) {
      error(builder, JavaErrorBundle.message("expected.while"));
    }
    else if (parseExprInParenth(builder)) {
      semicolon(builder);
    }

    done(statement, JavaElementType.DO_WHILE_STATEMENT);
    return statement;
  }

  @Nonnull
  private PsiBuilder.Marker parseSwitchStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, JavaElementType.SWITCH_STATEMENT, true);
  }

  /**
   * @return marker and whether it contains expression inside
   */
  @Nonnull
  public Pair<PsiBuilder.Marker, Boolean> parseCaseLabel(PsiBuilder builder) {
    if (builder.getTokenType() == JavaTokenType.DEFAULT_KEYWORD) {
      PsiBuilder.Marker defaultElement = builder.mark();
      builder.advanceLexer();
      done(defaultElement, JavaElementType.DEFAULT_CASE_LABEL_ELEMENT);
      return Pair.create(defaultElement, false);
    }
    if (myParser.getPatternParser().isPattern(builder)) {
      PsiBuilder.Marker pattern = myParser.getPatternParser().parsePattern(builder);
      return Pair.create(pattern, false);
    }
    return Pair.create(myParser.getExpressionParser().parseAssignmentForbiddingLambda(builder), true);
  }


  private PsiBuilder.Marker parseSwitchLabelStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    boolean isCase = builder.getTokenType() == JavaTokenType.CASE_KEYWORD;
    builder.advanceLexer();

    if (isCase) {
      boolean patternsAllowed = getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_17);
      PsiBuilder.Marker list = builder.mark();
      do {
        Pair<PsiBuilder.Marker, Boolean> markerAndIsExpression = parseCaseLabel(builder);
        PsiBuilder.Marker caseLabel = markerAndIsExpression.first;
        if (caseLabel == null) {
          error(builder, JavaPsiBundle.message(patternsAllowed ? "expected.case.label.element" : "expected.expression"));
        }
      }
      while (expect(builder, JavaTokenType.COMMA));
      done(list, JavaElementType.CASE_LABEL_ELEMENT_LIST);
      parseGuard(builder);
    }

    if (expect(builder, JavaTokenType.ARROW)) {
      PsiBuilder.Marker expr;
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        PsiBuilder.Marker body = builder.mark();
        parseCodeBlock(builder, true);
        body.done(JavaElementType.BLOCK_STATEMENT);
        if (builder.getTokenType() == JavaTokenType.SEMICOLON) {
          PsiBuilder.Marker mark = builder.mark();
          while (builder.getTokenType() == JavaTokenType.SEMICOLON) builder.advanceLexer();
          mark.error(JavaPsiBundle.message("expected.switch.label"));
        }
      }
      else if (builder.getTokenType() == JavaTokenType.THROW_KEYWORD) {
        parseThrowStatement(builder);
      }
      else if ((expr = myParser.getExpressionParser().parse(builder)) != null) {
        PsiBuilder.Marker body = expr.precede();
        semicolon(builder);
        body.done(JavaElementType.EXPRESSION_STATEMENT);
      }
      else {
        error(builder, JavaPsiBundle.message("expected.switch.rule"));
        expect(builder, JavaTokenType.SEMICOLON);
      }
      done(statement, JavaElementType.SWITCH_LABELED_RULE);
    }
    else {
      expectOrError(builder, JavaTokenType.COLON, "expected.colon.or.arrow");
      done(statement, JavaElementType.SWITCH_LABEL_STATEMENT);
    }

    return statement;
  }

  private void parseGuard(PsiBuilder builder) {
    if (builder.getTokenType() == JavaTokenType.IDENTIFIER && PsiKeyword.WHEN.equals(builder.getTokenText())) {
      builder.remapCurrentToken(JavaTokenType.WHEN_KEYWORD);
      builder.advanceLexer();
      PsiBuilder.Marker guardingExpression = myParser.getExpressionParser().parseAssignmentForbiddingLambda(builder);
      if (guardingExpression == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }
    }
  }

  @Nonnull
  private static PsiBuilder.Marker parseBreakStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    done(statement, JavaElementType.BREAK_STATEMENT);
    return statement;
  }

  @Nonnull
  private PsiBuilder.Marker parseYieldStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.remapCurrentToken(JavaTokenType.YIELD_KEYWORD);
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, JavaElementType.YIELD_STATEMENT);
    return statement;
  }

  @Nonnull
  private static PsiBuilder.Marker parseContinueStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    done(statement, JavaElementType.CONTINUE_STATEMENT);
    return statement;
  }

  @jakarta.annotation.Nonnull
  private PsiBuilder.Marker parseReturnStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    myParser.getExpressionParser().parse(builder);
    semicolon(builder);
    done(statement, JavaElementType.RETURN_STATEMENT);
    return statement;
  }

  @Nonnull
  private PsiBuilder.Marker parseThrowStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, JavaElementType.THROW_STATEMENT);
    return statement;
  }

  @Nonnull
  private PsiBuilder.Marker parseSynchronizedStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, JavaElementType.SYNCHRONIZED_STATEMENT, true);
  }

  @Nonnull
  private PsiBuilder.Marker parseTryStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    boolean hasResourceList = builder.getTokenType() == JavaTokenType.LPARENTH;
    if (hasResourceList) {
      myParser.getDeclarationParser().parseResourceList(builder);
    }

    PsiBuilder.Marker tryBlock = parseCodeBlock(builder, true);
    if (tryBlock == null) {
      error(builder, JavaErrorBundle.message("expected.lbrace"));
    }
    else if (!hasResourceList && !TRY_CLOSERS_SET.contains(builder.getTokenType())) {
      error(builder, JavaErrorBundle.message("expected.catch.or.finally"));
    }
    else {
      while (builder.getTokenType() == JavaTokenType.CATCH_KEYWORD) {
        if (!parseCatchBlock(builder)) {
          break;
        }
      }

      if (expect(builder, JavaTokenType.FINALLY_KEYWORD)) {
        PsiBuilder.Marker finallyBlock = parseCodeBlock(builder, true);
        if (finallyBlock == null) {
          error(builder, JavaErrorBundle.message("expected.lbrace"));
        }
      }
    }

    done(statement, JavaElementType.TRY_STATEMENT);
    return statement;
  }

  public boolean parseCatchBlock(@Nonnull PsiBuilder builder) {
    assert builder.getTokenType() == JavaTokenType.CATCH_KEYWORD : builder.getTokenType();
    PsiBuilder.Marker section = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorBundle.message("expected.lparen"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    PsiBuilder.Marker param = myParser.getDeclarationParser().parseParameter(builder, false, true, false);
    if (param == null) {
      error(builder, JavaErrorBundle.message("expected.parameter"));
    }

    if (!expect(builder, JavaTokenType.RPARENTH)) {
      error(builder, JavaErrorBundle.message("expected.rparen"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    PsiBuilder.Marker body = parseCodeBlock(builder, true);
    if (body == null) {
      error(builder, JavaErrorBundle.message("expected.lbrace"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    done(section, JavaElementType.CATCH_SECTION);
    return true;
  }

  @Nonnull
  private PsiBuilder.Marker parseAssertStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.boolean.expression"));
    }
    else if (expect(builder, JavaTokenType.COLON) && myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, JavaElementType.ASSERT_STATEMENT);
    return statement;
  }

  @Nonnull
  private PsiBuilder.Marker parseBlockStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    parseCodeBlock(builder, true);
    done(statement, JavaElementType.BLOCK_STATEMENT);
    return statement;
  }

  @Nonnull
  public PsiBuilder.Marker parseExprInParenthWithBlock(@Nonnull PsiBuilder builder, @Nonnull IElementType type, boolean block) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (parseExprInParenth(builder)) {
      PsiBuilder.Marker body = block ? parseCodeBlock(builder, true) : parseStatement(builder);
      if (body == null) {
        error(builder, JavaErrorBundle.message(block ? "expected.lbrace" : "expected.statement"));
      }
    }

    done(statement, type);
    return statement;
  }

  private boolean parseExprInParenth(PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorBundle.message("expected.lparen"));
      return false;
    }

    PsiBuilder.Marker beforeExpr = builder.mark();
    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null || builder.getTokenType() == JavaTokenType.SEMICOLON) {
      beforeExpr.rollbackTo();
      error(builder, JavaErrorBundle.message("expected.expression"));
      if (builder.getTokenType() != JavaTokenType.RPARENTH) {
        return false;
      }
    }
    else {
      beforeExpr.drop();
      if (builder.getTokenType() != JavaTokenType.RPARENTH) {
        error(builder, JavaErrorBundle.message("expected.rparen"));
        return false;
      }
    }

    builder.advanceLexer();
    return true;
  }
}