// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.parser.WhitespacesAndCommentsBinder;
import consulo.util.lang.StringUtil;

import java.util.List;

public final class WhiteSpaceAndCommentSetHolder {
  private static final WhitespacesAndCommentsBinder PRECEDING_COMMENT_BINDER_WITH_MARKDOWN =
    new PrecedingWhitespacesAndCommentsBinder(false, true);
  private static final WhitespacesAndCommentsBinder SPECIAL_PRECEDING_COMMENT_BINDER_WITH_MARKDOWN =
    new PrecedingWhitespacesAndCommentsBinder(true, true);
  private static final WhitespacesAndCommentsBinder PRECEDING_COMMENT_BINDER_WITHOUT_MARKDOWN =
    new PrecedingWhitespacesAndCommentsBinder(false, false);
  private static final WhitespacesAndCommentsBinder SPECIAL_PRECEDING_COMMENT_BINDER_WITHOUT_MARKDOWN =
    new PrecedingWhitespacesAndCommentsBinder(true, false);
  private static final WhitespacesAndCommentsBinder TRAILING_COMMENT_BINDER = new TrailingWhitespacesAndCommentsBinder();

  private static final TokenSet PRECEDING_COMMENT_SET =
    TokenSet.orSet(TokenSet.create(JavaElementType.MODULE, JavaElementType.IMPLICIT_CLASS), ElementType.FULL_MEMBER_BIT_SET);

  private static final TokenSet TRAILING_COMMENT_SET =
    TokenSet.orSet(TokenSet.create(JavaElementType.PACKAGE_STATEMENT), ElementType.IMPORT_STATEMENT_BASE_BIT_SET,
                   ElementType.FULL_MEMBER_BIT_SET, ElementType.JAVA_STATEMENT_BIT_SET);

  private WhiteSpaceAndCommentSetHolder() {
  }

  public static WhitespacesAndCommentsBinder getPrecedingCommentBinder(LanguageLevel myLanguageLevel) {
    return JavaFeature.MARKDOWN_COMMENT.isSufficient(myLanguageLevel)
           ? PRECEDING_COMMENT_BINDER_WITH_MARKDOWN
           : PRECEDING_COMMENT_BINDER_WITHOUT_MARKDOWN;
  }

  public static WhitespacesAndCommentsBinder getSpecialPrecedingCommentBinder(LanguageLevel myLanguageLevel) {
    return JavaFeature.MARKDOWN_COMMENT.isSufficient(myLanguageLevel)
           ? SPECIAL_PRECEDING_COMMENT_BINDER_WITH_MARKDOWN
           : SPECIAL_PRECEDING_COMMENT_BINDER_WITHOUT_MARKDOWN;
  }

  public static WhitespacesAndCommentsBinder getTrailingCommentBinder() {
    return TRAILING_COMMENT_BINDER;
  }

  public static TokenSet getPrecedingCommentSet() {
    return PRECEDING_COMMENT_SET;
  }

  public static TokenSet getTrailingCommentSet() {
    return TRAILING_COMMENT_SET;
  }

  private static class PrecedingWhitespacesAndCommentsBinder implements WhitespacesAndCommentsBinder {
    private final boolean myAfterEmptyImport;
    private final boolean mySupportMarkdown;

    PrecedingWhitespacesAndCommentsBinder(boolean afterImport, boolean supportMarkdown) {
      myAfterEmptyImport = afterImport;
      mySupportMarkdown = supportMarkdown;
    }

    @Override
    public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
      if (tokens.isEmpty()) {
        return 0;
      }

      // 1. bind doc comment
      // now there are markdown comments.
      if (mySupportMarkdown) {
        //collect everything
        for (int idx = tokens.size() - 1; idx >= 0; idx--) {
          if (tokens.get(idx) == JavaDocElementType.DOC_COMMENT || tokens.get(idx) == JavaDocElementType.DOC_MARKDOWN_COMMENT) {
            return idx;
          }
        }
      }
      else {
        // To preserve previous orders, let's try to find the first non-markdown comment (and skip markdown comments).
        // If there is no non-markdown, take the first markdown
        for (int idx = tokens.size() - 1; idx >= 0; idx--) {
          if (tokens.get(idx) == JavaDocElementType.DOC_COMMENT) {
            return idx;
          }
        }

        for (int idx = tokens.size() - 1; idx >= 0; idx--) {
          if (tokens.get(idx) == JavaDocElementType.DOC_MARKDOWN_COMMENT) {
            return idx;
          }
        }
      }

      // 2. bind plain comments
      int result = tokens.size();
      for (int idx = tokens.size() - 1; idx >= 0; idx--) {
        IElementType tokenType = tokens.get(idx);
        if (TokenSet.WHITE_SPACE.contains(tokenType)) {
          if (StringUtil.getLineBreakCount(getter.get(idx)) > 1) {
            break;
          }
        }
        else if (ElementType.JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
          if (atStreamEdge ||
              (idx == 0 && myAfterEmptyImport) ||
              (idx > 0 && TokenSet.WHITE_SPACE.contains(tokens.get(idx - 1)) && StringUtil.containsLineBreak(getter.get(idx - 1)))) {
            result = idx;
          }
        }
        else {
          break;
        }
      }

      return result;
    }
  }

  private static class TrailingWhitespacesAndCommentsBinder implements WhitespacesAndCommentsBinder {
    @Override
    public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
      if (tokens.isEmpty()) {
        return 0;
      }

      int result = 0;
      for (int idx = 0; idx < tokens.size(); idx++) {
        IElementType tokenType = tokens.get(idx);
        if (TokenSet.WHITE_SPACE.contains(tokenType)) {
          if (StringUtil.containsLineBreak(getter.get(idx))) {
            break;
          }
        }
        else if (ElementType.JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
          result = idx + 1;
        }
        else {
          break;
        }
      }

      return result;
    }
  }
}
