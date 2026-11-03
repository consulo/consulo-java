// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.parser;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaDocTokenType;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.ast.TokenType;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiBuilderUtil;
import consulo.language.parser.WhitespacesAndCommentsBinder;
import consulo.language.parser.WhitespacesBinders;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class JavaDocParser {
  /**
   * Binder taking the next space if it isn't an EOL
   */
  private static final WhitespacesAndCommentsBinder FAKE_COLLAPSE_RIGHT_BINDER = (tokens, atStreamEdge, getter) -> {
    if (!tokens.isEmpty() && !isEolToken(tokens.get(0), getter.get(0))) {
      return 1;
    }
    return 0;
  };

  private static final TokenSet TAG_VALUES_SET = TokenSet.create(
    JavaDocTokenType.DOC_TAG_VALUE_TOKEN, JavaDocTokenType.DOC_TAG_VALUE_COMMA, JavaDocTokenType.DOC_TAG_VALUE_DOT,
    JavaDocTokenType.DOC_TAG_VALUE_LPAREN, JavaDocTokenType.DOC_TAG_VALUE_RPAREN,
    JavaDocTokenType.DOC_TAG_VALUE_SLASH, JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN, JavaDocTokenType.DOC_TAG_VALUE_DOUBLE_SHARP_TOKEN,
    JavaDocTokenType.DOC_TAG_VALUE_LT, JavaDocTokenType.DOC_TAG_VALUE_GT, JavaDocTokenType.DOC_TAG_VALUE_COLON,
    JavaDocTokenType.DOC_TAG_VALUE_QUOTE
  );

  private static final TokenSet INLINE_TAG_BORDERS_SET = TokenSet.create(
    JavaDocTokenType.DOC_INLINE_TAG_START, JavaDocTokenType.DOC_INLINE_TAG_END);

  private static final TokenSet SKIP_TOKENS = TokenSet.create(JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS);

  private static final TokenSet COMMENT_DATA_TOKENS = TokenSet.create(
    JavaDocTokenType.DOC_COMMENT_DATA,
    JavaDocTokenType.DOC_TAG_VALUE_SLASH,
    JavaDocTokenType.DOC_COMMA,
    JavaDocTokenType.DOC_SHARP, JavaDocTokenType.DOC_DOUBLE_SHARP,
    JavaDocTokenType.DOC_LT, JavaDocTokenType.DOC_GT
  );

  /**
   * Represent the expected token at the caret for Markdown link {@code [Class<caret>#method()]}
   */
  private static final TokenSet REFERENCE_LINK_EOC_EXPECTED_TOKENS = TokenSet.create(
    JavaDocTokenType.DOC_RBRACKET, JavaDocTokenType.DOC_LT,
    JavaDocTokenType.DOC_SHARP, JavaDocTokenType.DOC_DOUBLE_SHARP
  );

  /**
   * Represent the allowed base tokens when parsing generics
   */
  private static final TokenSet DATA_IN_GENERICS_TOKENS = TokenSet.create(
    JavaDocTokenType.DOC_TAG_VALUE_TOKEN, JavaDocTokenType.DOC_COMMENT_DATA,
    JavaDocTokenType.DOC_TAG_VALUE_COMMA, JavaDocTokenType.DOC_COMMA,
    JavaDocTokenType.DOC_SPACE, TokenType.WHITE_SPACE
  );
  private static final TokenSet LT_TOKENS = TokenSet.create(JavaDocTokenType.DOC_LT, JavaDocTokenType.DOC_TAG_VALUE_LT);
  private static final TokenSet GT_TOKENS = TokenSet.create(JavaDocTokenType.DOC_GT, JavaDocTokenType.DOC_TAG_VALUE_GT);

  private static final String SEE_TAG = "@see";
  private static final String LINK_TAG = "@link";
  private static final String LINK_PLAIN_TAG = "@linkplain";
  private static final String PARAM_TAG = "@param";
  private static final String VALUE_TAG = "@value";
  private static final String SNIPPET_TAG = "@snippet";
  private static final String INHERIT_DOC_TAG = "@inheritDoc";

  private static final Set<String> REFERENCE_TAGS = Set.of("@throws", "@exception", "@provides", "@uses");

  private final PsiBuilder builder;
  private final LanguageLevel languageLevel;

  private int braceScope;
  private final List<Boolean> closingStatusList = new ArrayList<>(); // true for all '{' opening an inline tag

  public JavaDocParser(PsiBuilder builder, LanguageLevel languageLevel) {
    this.builder = builder;
    this.languageLevel = languageLevel;
  }

  public void parseJavadocReference(JavaParser parser) {
    parser.getReferenceParser().parseJavaCodeReference(builder, true, true, false, true);
    swallowTokens();
  }

  public void parseJavadocType(JavaParser parser) {
    parser.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD);
    swallowTokens();
  }

  private void swallowTokens() {
    while (!builder.eof()) {
      builder.advanceLexer();
    }
  }

  public void parseDocCommentText() {
    builder.enforceCommentTokens(SKIP_TOKENS);

    while (!builder.eof()) {
      IElementType tokenType = getTokenType();
      if (tokenType == JavaDocTokenType.DOC_TAG_NAME) {
        parseTag();
      }
      else {
        parseDataItem(null, false);
      }
    }
  }

  private void parseTag() {
    String tagName = builder.getTokenText();
    PsiBuilder.Marker tag = builder.mark();
    builder.advanceLexer();
    while (true) {
      IElementType tokenType = getTokenType();
      if (tokenType == null || tokenType == JavaDocTokenType.DOC_TAG_NAME || tokenType == JavaDocTokenType.DOC_COMMENT_END) {
        break;
      }
      parseDataItem(tagName, false);
    }
    tag.done(JavaDocElementType.DOC_TAG);
  }

  private void parseDataItem(@Nullable String tagName, boolean isInline) {
    IElementType tokenType = getTokenType();
    if (tokenType == JavaDocTokenType.DOC_INLINE_TAG_START) {
      PsiBuilder.Marker tag = builder.mark();
      builder.advanceLexer();

      tokenType = getTokenType();
      if (tokenType != JavaDocTokenType.DOC_TAG_NAME && tokenType != JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
        tag.rollbackTo();
        builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
        builder.advanceLexer();
        closingStatusList.add(false);
        return;
      }

      closingStatusList.add(true);

      String inlineTagName = "";
      boolean isSnippet = false;

      while (true) {
        tokenType = getTokenType();
        if (tokenType == JavaDocTokenType.DOC_TAG_NAME) {
          inlineTagName = builder.getTokenText();
          isSnippet = SNIPPET_TAG.equals(inlineTagName);
        }
        else if (tokenType == null || tokenType == JavaDocTokenType.DOC_COMMENT_END) {
          break;
        }

        if (tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) {
          boolean shouldClose = closingStatusList.remove(closingStatusList.size() - 1);
          if (shouldClose) {
            setBraceScope(getBraceScope() - 1);
            builder.advanceLexer();
            break;
          }
          else {
            builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
            builder.advanceLexer();
            continue;
          }
        }

        parseDataItem(inlineTagName, true);
      }

      if (isSnippet) {
        tag.done(JavaDocElementType.DOC_SNIPPET_TAG);
      }
      else {
        tag.done(JavaDocElementType.DOC_INLINE_TAG);
      }
    }
    else if (TAG_VALUES_SET.contains(tokenType)) {
      if (SEE_TAG.equals(tagName) && !isInline ||
          LINK_TAG.equals(tagName) && isInline ||
          languageLevel.isAtLeast(LanguageLevel.JDK_1_4) && LINK_PLAIN_TAG.equals(tagName) && isInline) {
        parseSeeTagValue(false);
      }
      else if (!isInline && tagName != null && REFERENCE_TAGS.contains(tagName) || isInline && INHERIT_DOC_TAG.equals(tagName)) {
        PsiBuilder.Marker tagValue = builder.mark();
        builder.remapCurrentToken(JavaDocElementType.DOC_REFERENCE_HOLDER);
        builder.advanceLexer();
        tagValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
      }
      else if (!isInline && PARAM_TAG.equals(tagName)) {
        parseParameterRef();
      }
      else if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && VALUE_TAG.equals(tagName) && isInline) {
        parseSeeTagValue(true);
      }
      else if (SNIPPET_TAG.equals(tagName) && isInline) {
        parseSnippetTagValue();
      }
      else {
        parseSimpleTagValue();
      }
    }
    else if (tokenType == JavaDocTokenType.DOC_INLINE_CODE_FENCE) {
      parseInlineCodeBlock();
    }
    else if (tokenType == JavaDocTokenType.DOC_CODE_FENCE) {
      parseCodeBlock();
    }
    else if (tokenType == JavaDocTokenType.DOC_LBRACKET) {
      parseMarkdownReferenceOrLink();
    }
    else if (COMMENT_DATA_TOKENS.contains(tokenType) || (isWhiteSpace(tokenType) && !isEolToken(tokenType, builder.getTokenText()))) {
      parseCommentData();
    }
    else {
      remapAndAdvance();
    }
  }

  private void parseCommentData() {
    PsiBuilder.Marker commentData = builder.mark();
    int offset = builder.getCurrentOffset();

    IElementType aheadType;
    while ((aheadType = builder.rawLookup(1)) != null) {
      boolean hasCommentTokenNext = COMMENT_DATA_TOKENS.contains(aheadType);
      boolean hasWhiteSpaceTokenNext = isWhiteSpace(aheadType) && !isEolToken(aheadType, PsiBuilderUtil.rawTokenText(builder, 1));

      if (hasCommentTokenNext || hasWhiteSpaceTokenNext) {
        builder.rawAdvanceLexer(1);
      }
      else {
        break;
      }
    }

    if (builder.getCurrentOffset() != offset) {
      builder.advanceLexer();
      commentData.collapse(JavaDocTokenType.DOC_COMMENT_DATA);
    }
    else {
      commentData.drop();
      builder.advanceLexer();
    }
  }

  private void parseInlineCodeBlock() {
    PsiBuilder.Marker blockMarker = builder.mark();
    builder.rawAdvanceLexer(1);
    PsiBuilder.Marker contentMarker = builder.mark();
    IElementType stopElementType = findInlineToken(JavaDocTokenType.DOC_INLINE_CODE_FENCE);

    if (stopElementType != JavaDocTokenType.DOC_INLINE_CODE_FENCE) {
      // Bail out, no end
      contentMarker.drop();
      blockMarker.rollbackTo();
      builder.advanceLexer();
      return;
    }

    fakeCollapse(contentMarker, JavaDocTokenType.DOC_COMMENT_DATA);
    if (!builder.eof()) {
      builder.advanceLexer();
    }

    blockMarker.done(JavaDocElementType.DOC_MARKDOWN_CODE_BLOCK);
  }

  private void parseCodeBlock() {
    if (getBraceScope() > 0) {
      builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
      builder.advanceLexer();
      return;
    }
    // Store the fence type, a fenced code block can only be closed by the same fence type
    char fenceStart = Objects.requireNonNull(builder.getTokenText()).charAt(0);
    PsiBuilder.Marker tag = builder.mark();

    // Look for the nearest closing code fence, converting everything inside as comment data
    while (!builder.eof()) {
      builder.advanceLexer();
      if (getTokenType() == JavaDocTokenType.DOC_CODE_FENCE && Objects.requireNonNull(builder.getTokenText()).charAt(0) == fenceStart) {
        break;
      }
      builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
    }

    if (!builder.eof()) {
      builder.advanceLexer();
    }

    tag.done(JavaDocElementType.DOC_MARKDOWN_CODE_BLOCK);
  }

  /**
   * Ensure a link is good before parsing it
   */
  private void parseMarkdownReferenceOrLink() {
    boolean hasLabel = true;
    PsiBuilder.Marker tag = builder.mark();

    // Step 1 ensure that we have a label
    int leftBracketCount = 1;
    int rightBracketCount = 0;
    int startLabelOffset = builder.getCurrentOffset();

    // Labels are allowed balanced brackets, count each side
    while (!builder.eof()) {
      IElementType token = findInlineToken(JavaDocTokenType.DOC_RBRACKET, JavaDocTokenType.DOC_LBRACKET, true);
      if (token == JavaDocTokenType.DOC_LBRACKET) {
        leftBracketCount++;
        continue;
      }
      if (token == JavaDocTokenType.DOC_RBRACKET) {
        rightBracketCount++;
        if (leftBracketCount == rightBracketCount) {
          break;
        }
      }
    }

    int endLabelOffset = builder.getCurrentOffset();
    boolean isShortRefEmpty = endLabelOffset - startLabelOffset <= 1;
    if (leftBracketCount != rightBracketCount || isShortRefEmpty) {
      // Stop if unbalanced brackets/empty reference
      tag.rollbackTo();
      builder.advanceLexer();
      return;
    }

    IElementType firstReferenceToken = findInlineToken(JavaDocTokenType.DOC_LBRACKET, JavaDocTokenType.DOC_SPACE, false);
    if (firstReferenceToken != JavaDocTokenType.DOC_LBRACKET) {
      hasLabel = false;
      // The label may actually be a reference, verify brackets balance or if we have a normal Markdown link
      if (leftBracketCount > 1 || firstReferenceToken == JavaDocTokenType.DOC_LPAREN) {
        tag.rollbackTo();
        parseMarkdownLink();
        return;
      }
    }

    // Step 2 get the reference for full reference link
    if (hasLabel) {
      if (findInlineToken(JavaDocTokenType.DOC_RBRACKET, JavaDocTokenType.DOC_LBRACKET, true) != JavaDocTokenType.DOC_RBRACKET) {
        hasLabel = false;
        // The label is actually a reference, verify brackets balance
        if (leftBracketCount > 1) {
          tag.rollbackTo();
          builder.advanceLexer();
          return;
        }
      }
    }

    // Step 3, validity ensured, parse the link
    tag.rollbackTo();
    tag = builder.mark();
    if (hasLabel) {
      builder.advanceLexer();
      PsiBuilder.Marker label = builder.mark();
      /* Collapses comment data within a label */
      PsiBuilder.Marker commentDataMarker = null;
      // Label range already known, mark it as comment data
      while (!builder.eof()) {
        if (builder.getTokenType() == JavaDocTokenType.DOC_INLINE_CODE_FENCE) {
          if (commentDataMarker != null) {
            fakeCollapse(commentDataMarker, JavaDocTokenType.DOC_COMMENT_DATA);
            commentDataMarker = null;
          }
          parseInlineCodeBlock();
          continue;
        }

        if (builder.getCurrentOffset() < endLabelOffset) {
          if (commentDataMarker == null) {
            commentDataMarker = builder.mark();
          }
          builder.advanceLexer();
          continue;
        }
        break;
      }

      if (commentDataMarker != null) {
        fakeCollapse(commentDataMarker, JavaDocTokenType.DOC_COMMENT_DATA);
      }
      label.done(JavaDocElementType.DOC_MARKDOWN_REFERENCE_LABEL);
      builder.advanceLexer();
    }

    // Parse the reference itself
    builder.advanceLexer();
    if (parseMarkdownReference()) {
      builder.advanceLexer();
      tag.done(JavaDocElementType.DOC_MARKDOWN_REFERENCE_LINK);
    }
    else {
      tag.drop();
    }
  }

  /**
   * @return Whether the reference managed to get parsed
   */
  private boolean parseMarkdownReference() {
    PsiBuilder.Marker moduleMarker = parseModuleRef(builder.mark());
    boolean referenceParsed = false;
    boolean referenceEnded = false;
    boolean fragmentReference = false;

    if (getTokenType() == JavaDocTokenType.DOC_RBRACKET) {
      if (moduleMarker == null) {
        return false;
      }
      referenceEnded = true;
    }
    if (moduleMarker != null) {
      moduleMarker.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
    }
    PsiBuilder.Marker refStart = builder.mark();
    boolean isMethodFieldOrRef = false;
    if (!referenceEnded && getTokenType() != JavaDocTokenType.DOC_SHARP && getTokenType() != JavaDocTokenType.DOC_DOUBLE_SHARP) {
      // Javadoc methods references may not have the # token if it is alone, rely on the existence of () to assign the proper type.
      // In practice, () is not mandatory, and fields have the same issue but cannot be separated from class names on parsing
      if (builder.lookAhead(1) == JavaDocTokenType.DOC_LPAREN) {
        isMethodFieldOrRef = true;
      }
      else if (!REFERENCE_LINK_EOC_EXPECTED_TOKENS.contains(builder.lookAhead(1))) {
        // Additional unexpected data: "[String unlawfulData]"
        refStart.rollbackTo();
        return false;
      }
      else {
        parseMaybeGenericType(true);
      }
    }

    if (!referenceEnded && !isMethodFieldOrRef) {
      isMethodFieldOrRef = getTokenType() == JavaDocTokenType.DOC_SHARP;
      if (isMethodFieldOrRef) {
        // Existing integration require this token for auto completion
        builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN);

        builder.advanceLexer();
      }
    }

    if (!referenceEnded && isMethodFieldOrRef) {
      // method/variable name
      builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);

      // A method only has parenthesis, comment data which may be the type, the optional argument name and commas
      builder.advanceLexer();
      if (builder.getTokenType() == JavaDocTokenType.DOC_LPAREN) {
        builder.advanceLexer();
        PsiBuilder.Marker subValue = builder.mark();

        // Only the first data element count as a type in links like [#foo(int arg1, int arg2)]
        boolean dataSinceComma = false;
        int spaceCount = 0;

        while (!builder.eof()) {
          IElementType type = getTokenType();
          if (type == JavaDocTokenType.DOC_COMMENT_DATA) {
            if (isWhiteSpace(builder.rawLookup(1))) {
              spaceCount++;
            }
            if (spaceCount >= 2) {
              // Unexpected data: [#foo(int desc unexpectedData)]
              subValue.drop();
              fakeCollapse(refStart, JavaDocTokenType.DOC_COMMENT_DATA);
              return false;
            }

            if (!dataSinceComma) {
              dataSinceComma = true;
              parseMaybeGenericType(false);
            }
            else {
              builder.advanceLexer();
            }
          }
          else if (type != JavaDocTokenType.DOC_COMMA) {
            break;
          }
          else {
            dataSinceComma = false;
            builder.advanceLexer();
          }
        }

        if (getTokenType() == JavaDocTokenType.DOC_RPAREN) {
          subValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
          builder.advanceLexer();
        }
        else {
          subValue.drop();
        }
      }

      referenceParsed = true;
    }
    else if (!referenceEnded && getTokenType() == JavaDocTokenType.DOC_DOUBLE_SHARP) {
      // auto-completion token
      builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_DOUBLE_SHARP_TOKEN);
      builder.advanceLexer();

      // fragment name
      PsiBuilder.Marker nameMarker = builder.mark();

      if (getTokenType() != JavaDocTokenType.DOC_COMMENT_DATA) {
        nameMarker.rollbackTo();
      }
      else {
        builder.advanceLexer();
        nameMarker.done(JavaDocElementType.DOC_FRAGMENT_NAME);
        referenceParsed = true;
        fragmentReference = true;
      }
    }

    if (referenceParsed) {
      if (fragmentReference) {
        refStart.done(JavaDocElementType.DOC_FRAGMENT_REF);
      }
      else {
        refStart.done(JavaDocElementType.DOC_METHOD_OR_FIELD_REF);
      }
    }
    else {
      refStart.drop();
    }
    // This method is guaranteed the existence of an end bracket
    if (getTokenType() != JavaDocTokenType.DOC_RBRACKET) {
      findInlineToken(JavaDocTokenType.DOC_RBRACKET);
    }

    return true;
  }

  /**
   * Simple parse function that will wrap a Markdown link in an element
   */
  private void parseMarkdownLink() {
    PsiBuilder.Marker fallback = builder.mark();
    if (builder.getTokenType() != JavaDocTokenType.DOC_LBRACKET) {
      fallback.rollbackTo();
      builder.advanceLexer();
      return;
    }
    builder.advanceLexer();
    PsiBuilder.Marker label = builder.mark();
    findInlineToken(JavaDocTokenType.DOC_RBRACKET, JavaDocTokenType.DOC_LBRACKET, true);
    fakeCollapse(label, JavaDocTokenType.DOC_COMMENT_DATA);

    if (builder.getTokenType() != JavaDocTokenType.DOC_RBRACKET) {
      fallback.rollbackTo();
      builder.advanceLexer();
      return;
    }

    builder.advanceLexer();
    if (builder.getTokenType() != JavaDocTokenType.DOC_LPAREN) {
      fallback.rollbackTo();
      builder.advanceLexer();
      return;
    }

    builder.advanceLexer();
    PsiBuilder.Marker link = builder.mark();
    findInlineToken(JavaDocTokenType.DOC_RPAREN);

    if (builder.getTokenType() != JavaDocTokenType.DOC_RPAREN) {
      fallback.rollbackTo();
      builder.advanceLexer();
      return;
    }

    fakeCollapse(link, JavaDocTokenType.DOC_COMMENT_DATA);
    builder.advanceLexer();
    fallback.done(JavaDocElementType.DOC_MARKDOWN_LINK);
  }

  private @Nullable IElementType findInlineToken(@Nullable IElementType needle) {
    return findInlineToken(needle, null, false);
  }

  /**
   * Look for the token provided by {@code needle}, taking into account markdown line break rules
   *
   * @param travelToken             The token that is either allowed or disallowed to encounter while looking for the {@code needle}
   *                                When {@code null}, no check is performed
   * @param isTravelTokenDisallowed When {@code true}, the {@code travelToken} will abort the search
   *                                When {@code false}, encountering something other than {@code travelToken} or {@code needle} will abort the search
   * @return The last token encountered during the search.
   */
  private @Nullable IElementType findInlineToken(@Nullable IElementType needle,
                                                 @Nullable IElementType travelToken,
                                                 boolean isTravelTokenDisallowed) {
    IElementType token = null;
    IElementType previousToken;
    while (!builder.eof()) {
      builder.advanceLexer();
      previousToken = token;
      token = getTokenType(false);
      if (token == needle) {
        return token;
      }
      boolean travelTokenFound = travelToken == token;
      if ((travelToken != null) && ((isTravelTokenDisallowed && travelTokenFound) || (!isTravelTokenDisallowed && !travelTokenFound))) {
        break;
      }

      // Markdown specific, check for EOL
      if (token == TokenType.WHITE_SPACE && previousToken == TokenType.WHITE_SPACE) {
        break;
      }
    }

    return builder.eof() ? null : token;
  }

  private void parseSnippetTagValue() {
    // we are right after @snippet
    PsiBuilder.Marker snippetValue = builder.mark();
    snippetValue.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER);

    // recovery, when "foo" goes right after @snippet
    while (true) {
      IElementType token = getTokenType();
      if (token != JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
        break;
      }
      builder.advanceLexer();
    }

    IElementType tokenType = getTokenType();
    if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_COLON) {
      JavaParserUtil.emptyElement(builder, JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST);
      parseSnippetTagBody();
    }
    else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      parseSnippetAttributeList();
      if (builder.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_COLON) {
        parseSnippetTagBody();
      }
    }
    else {
      JavaParserUtil.emptyElement(builder, JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST);
      IElementType current = getTokenType();
      while (current != null && current != JavaDocTokenType.DOC_INLINE_TAG_END) {
        builder.advanceLexer();
        current = getTokenType();
      }
    }
    snippetValue.done(JavaDocElementType.DOC_SNIPPET_TAG_VALUE);
  }

  private void parseSnippetTagBody() {
    PsiBuilder.Marker body = builder.mark();
    body.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    if (getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_COLON) {
      throw new IllegalArgumentException("Failed requirement.");
    }
    builder.advanceLexer();
    while (true) {
      IElementType tokenType = getTokenType();
      if (tokenType == null || tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) {
        break;
      }
      builder.advanceLexer();
    }
    body.done(JavaDocElementType.DOC_SNIPPET_BODY);
  }

  private void parseSnippetAttributeList() {
    PsiBuilder.Marker attributeList = builder.mark();
    outer:
    while (true) {
      IElementType type = getTokenType();
      while (type != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
        // recovery
        if (type != JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
          break outer;
        }
        builder.advanceLexer();
        type = getTokenType();
      }
      parseSnippetAttribute();
    }
    attributeList.done(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST);
  }

  private void parseSnippetAttribute() {
    PsiBuilder.Marker attribute = builder.mark();
    if (builder.getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      throw new IllegalArgumentException("Failed requirement.");
    }
    builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_ATTRIBUTE_NAME);
    builder.advanceLexer();
    getTokenType(); // skipping spaces
    if ("=".equals(builder.getTokenText())) {
      builder.advanceLexer();
      IElementType afterEqToken = getTokenType();
      if (afterEqToken == JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
        PsiBuilder.Marker quotedValue = builder.mark();
        builder.advanceLexer();
        if (getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          builder.advanceLexer();
        }
        if (getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
          builder.advanceLexer();
        }
        quotedValue.collapse(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_VALUE);
      }
      else if (afterEqToken == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
        builder.remapCurrentToken(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_VALUE);
        builder.advanceLexer();
      }
    }
    attribute.done(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE);
  }

  /**
   * Parse the reference inside a tag (like {@code @link} and {@code @see})
   *
   * @param allowBareFieldReference Whether bare references are <b>always</b> considered method/field refs
   */
  private void parseSeeTagValue(boolean allowBareFieldReference) {
    PsiBuilder.Marker moduleMarker = parseModuleRef(builder.mark());

    IElementType tokenType = getTokenType();
    if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      parseMethodRef(builder.mark());
    }
    else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_DOUBLE_SHARP_TOKEN) {
      parseFragmentRef(builder.mark());
    }
    else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      PsiBuilder.Marker refStart = builder.mark();
      parseMaybeGenericType(true);

      if (getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
        parseMethodRef(refStart);
      }
      else if (getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_DOUBLE_SHARP_TOKEN) {
        parseFragmentRef(refStart);
      }
      // Javadoc methods references may not have the # token if it is alone, rely on the existence of () to assign the proper type.
      // In practice, () is not mandatory, and fields have the same issue but cannot be separated from class names on parsing
      else if (allowBareFieldReference || getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_LPAREN) {
        refStart.rollbackTo();
        builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
        parseMethodRef(builder.mark());
      }
      else {
        refStart.drop();
      }
    }
    else if (moduleMarker == null) {
      PsiBuilder.Marker tagValue = builder.mark();
      builder.advanceLexer();
      tagValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
    }

    if (moduleMarker != null) {
      moduleMarker.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
    }
  }

  /**
   * Attempt to parse a class with a potential generic type attached to it (e.g. {@code List<String>}, {@code Map<String, String>})
   */
  private void parseMaybeGenericType(boolean isReferenceHolder) {
    PsiBuilder.Marker refStart = builder.mark();
    IElementType newElement = isReferenceHolder ? JavaDocElementType.DOC_REFERENCE_HOLDER : JavaDocElementType.DOC_TYPE_HOLDER;

    if (getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_TOKEN && getTokenType() != JavaDocTokenType.DOC_COMMENT_DATA) {
      refStart.rollbackTo();
      return;
    }

    builder.remapCurrentToken(newElement);
    builder.advanceLexer();

    if (LT_TOKENS.contains(getTokenType())) {
      int nesting = 1;
      while (!isEolToken(builder.getTokenType(), builder.getTokenText())) {
        builder.advanceLexer();
        if (DATA_IN_GENERICS_TOKENS.contains(builder.getTokenType())) {
          continue;
        }
        if (LT_TOKENS.contains(builder.getTokenType())) {
          nesting++;
          continue;
        }
        if (GT_TOKENS.contains(builder.getTokenType())) {
          nesting--;
          if (nesting == 0) {
            builder.advanceLexer();
            break;
          }
          continue;
        }

        // Disallowed token, break regardless of the nesting
        break;
      }
    }

    refStart.collapse(newElement);
  }

  private PsiBuilder.@Nullable Marker parseModuleRef(PsiBuilder.Marker refStart) {
    builder.advanceLexer();
    if (getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SLASH) {
      refStart.rollbackTo();
      PsiBuilder.Marker containerRef = builder.mark();
      PsiBuilder.Marker moduleRef = builder.mark();
      builder.advanceLexer();
      moduleRef.done(JavaElementType.MODULE_REFERENCE);
      builder.advanceLexer();
      return containerRef;
    }
    else {
      refStart.rollbackTo();
      return null;
    }
  }

  private void parseFragmentRef(PsiBuilder.Marker mark) {
    builder.advanceLexer();
    PsiBuilder.Marker fragmentName = builder.mark();
    if (getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      fragmentName.drop();
      mark.done(JavaDocElementType.DOC_FRAGMENT_REF);
    }
    else {
      builder.advanceLexer();
      fragmentName.done(JavaDocElementType.DOC_FRAGMENT_NAME);
      mark.done(JavaDocElementType.DOC_FRAGMENT_REF);
    }
  }

  private void parseMethodRef(PsiBuilder.Marker refStart) {
    if (getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      builder.advanceLexer();
    }
    if (getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      refStart.done(JavaDocElementType.DOC_METHOD_OR_FIELD_REF);
      return;
    }
    builder.advanceLexer();

    if (getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_LPAREN) {
      builder.advanceLexer();

      PsiBuilder.Marker subValue = builder.mark();

      IElementType tokenType;
      while (TAG_VALUES_SET.contains(tokenType = getTokenType())) {
        if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          parseMaybeGenericType(false);

          while (TAG_VALUES_SET.contains(tokenType = getTokenType()) &&
                 tokenType != JavaDocTokenType.DOC_TAG_VALUE_COMMA &&
                 tokenType != JavaDocTokenType.DOC_TAG_VALUE_RPAREN) {
            builder.advanceLexer();
          }
        }
        else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_RPAREN) {
          subValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
          builder.advanceLexer();
          refStart.done(JavaDocElementType.DOC_METHOD_OR_FIELD_REF);
          return;
        }
        else {
          builder.advanceLexer();
        }
      }

      subValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
    }

    refStart.done(JavaDocElementType.DOC_METHOD_OR_FIELD_REF);
  }

  private void parseParameterRef() {
    PsiBuilder.Marker tagValue = builder.mark();
    while (TAG_VALUES_SET.contains(getTokenType())) {
      builder.advanceLexer();
    }
    tagValue.done(JavaDocElementType.DOC_PARAMETER_REF);
  }

  private void parseSimpleTagValue() {
    PsiBuilder.Marker tagData = builder.mark();
    while (true) {
      IElementType tokenType = getTokenType();
      if (tokenType == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
        builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
      }
      else if (!TAG_VALUES_SET.contains(tokenType)) {
        break;
      }
      builder.advanceLexer();
    }
    tagData.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
  }

  /**
   * Behaves like-ish {@link PsiBuilder.Marker#collapse} but skips over eol and leading asterisks.
   * This means it cannot merge everything in a single node.
   * <p>
   * Note that {@code marker} will be dropped.
   */
  private void fakeCollapse(PsiBuilder.Marker marker, IElementType tokenType) {
    int endOffset = builder.getCurrentOffset();
    marker.rollbackTo();
    IElementType currentTokenType = getTokenType(false);
    PsiBuilder.Marker fuseMarker = shouldIgnoreInFakeCollapse(currentTokenType) ? null : builder.mark();

    while (!builder.eof() && builder.getCurrentOffset() < endOffset) {
      if (shouldIgnoreInFakeCollapse(currentTokenType)) {
        if (fuseMarker != null) {
          fuseMarker.collapse(tokenType);
          fuseMarker.setCustomEdgeTokenBinders(null, FAKE_COLLAPSE_RIGHT_BINDER);
        }
        fuseMarker = null;

        builder.advanceLexer();
        continue;
      }
      if (fuseMarker == null) {
        fuseMarker = builder.mark();
      }
      if (isWhiteSpace(builder.rawLookup(1)) && builder.rawLookup(2) == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
        builder.advanceLexer();
        fuseMarker.collapse(tokenType);
        fuseMarker.setCustomEdgeTokenBinders(null, FAKE_COLLAPSE_RIGHT_BINDER);
        fuseMarker = null;
      }
      else {
        builder.advanceLexer();
      }
    }
    if (fuseMarker != null) {
      fuseMarker.collapse(tokenType);
      fuseMarker.setCustomEdgeTokenBinders(null, FAKE_COLLAPSE_RIGHT_BINDER);
    }
  }

  /**
   * Whether a token should be ignored during the fake collapse
   */
  private boolean shouldIgnoreInFakeCollapse(@Nullable IElementType type) {
    return type != null &&
           (type == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS || isEolToken(type, builder.getTokenText()));
  }

  private @Nullable IElementType getTokenType() {
    return getTokenType(true);
  }

  private @Nullable IElementType getTokenType(boolean skipWhitespace) {
    IElementType tokenType;
    while ((tokenType = builder.getTokenType()) == JavaDocTokenType.DOC_SPACE) {
      builder.remapCurrentToken(TokenType.WHITE_SPACE);
      if (skipWhitespace) {
        builder.advanceLexer();
      }
    }
    return tokenType;
  }

  private int getBraceScope() {
    return braceScope;
  }

  private void setBraceScope(int braceScope) {
    this.braceScope = braceScope;
  }

  private void remapAndAdvance() {
    if (INLINE_TAG_BORDERS_SET.contains(builder.getTokenType()) && getBraceScope() == 0) {
      builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
    }
    builder.advanceLexer();
  }

  /**
   * @return Whether the token is eol
   */
  private static boolean isEolToken(@Nullable IElementType type, @Nullable CharSequence text) {
    return isWhiteSpace(type) && text != null && StringUtil.contains(text, "\n");
  }

  /**
   * @return Whether the token is a whitespace
   */
  private static boolean isWhiteSpace(@Nullable IElementType type) {
    return type != null && (type == JavaDocTokenType.DOC_SPACE || type == TokenType.WHITE_SPACE);
  }
}
