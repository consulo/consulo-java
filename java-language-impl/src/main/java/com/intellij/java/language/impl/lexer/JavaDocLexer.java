// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.lexer;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.lexer.JavaDocTokenTypes;
import com.intellij.java.language.lexer._JavaDocLexer;
import com.intellij.java.language.psi.JavaDocTokenType;
import consulo.language.ast.IElementType;
import consulo.language.lexer.LexerBase;
import consulo.language.lexer.MergingLexerAdapter;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public class JavaDocLexer extends MergingLexerAdapter {
  public JavaDocLexer(LanguageLevel level) {
    this(level.isAtLeast(LanguageLevel.JDK_1_5));
  }

  private JavaDocLexer(boolean isJdk15Enabled) {
    super(new AsteriskStripperLexer(new _JavaDocLexer(isJdk15Enabled)), JavaDocTokenTypes.INSTANCE.spaceCommentsTokenSet());
  }

  private static class AsteriskStripperLexer extends LexerBase {
    private final _JavaDocLexer myFlex;
    private CharSequence myBuffer;
    private int myBufferIndex;
    private int myBufferEndOffset;
    private int myTokenEndOffset;
    private int myState;
    private @Nullable IElementType myTokenType;
    private boolean myAfterLineBreak;
    private boolean myInLeadingSpace;
    private boolean myMarkdownMode;

    AsteriskStripperLexer(_JavaDocLexer flex) {
      myFlex = flex;
    }

    @Override
    public final void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer;
      myBufferIndex = startOffset;
      myBufferEndOffset = endOffset;
      myTokenType = null;
      myTokenEndOffset = startOffset;
      myAfterLineBreak = false;
      myInLeadingSpace = false;
      myFlex.reset(myBuffer, startOffset, endOffset, initialState);
      selectLexerMode(buffer, startOffset);
    }

    @Override
    public int getState() {
      return myState;
    }

    @Override
    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    @Override
    public int getBufferEnd() {
      return myBufferEndOffset;
    }

    @Override
    public final @Nullable IElementType getTokenType() {
      locateToken();
      return myTokenType;
    }

    @Override
    public final int getTokenStart() {
      locateToken();
      return myBufferIndex;
    }

    @Override
    public final int getTokenEnd() {
      locateToken();
      return myTokenEndOffset;
    }

    @Override
    public final void advance() {
      locateToken();
      myTokenType = null;
    }

    private void locateToken() {
      if (myTokenType != null) {
        return;
      }
      doLocateToken();

      if (myTokenType == JavaDocTokenType.DOC_SPACE) {
        myAfterLineBreak = CharArrayUtil.containLineBreaks(myBuffer, getTokenStart(), getTokenEnd());
      }
    }

    /**
     * Enables markdown mode if necessary
     */
    private void selectLexerMode(CharSequence buffer, int startOffset) {
      myMarkdownMode = StringUtil.startsWith(buffer, startOffset, "///");
      myFlex._reset(myMarkdownMode);
    }

    private void doLocateToken() {
      if (myTokenEndOffset == myBufferEndOffset) {
        myTokenType = null;
        myBufferIndex = myBufferEndOffset;
        return;
      }

      myBufferIndex = myTokenEndOffset;

      if (myAfterLineBreak) {
        myAfterLineBreak = false;

        if (myMarkdownMode) {
          while (detectLeadingSlashes()) {
            myTokenEndOffset += 3;
          }
        }
        else {
          while (detectLeadingAsterisks()) {
            myTokenEndOffset++;
          }
        }

        myInLeadingSpace = true;
        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
          return;
        }
      }

      if (myInLeadingSpace) {
        myInLeadingSpace = false;
        boolean lf = false;
        while (myTokenEndOffset < myBufferEndOffset && Character.isWhitespace(myBuffer.charAt(myTokenEndOffset))) {
          if (myBuffer.charAt(myTokenEndOffset) == '\n') {
            lf = true;
          }
          myTokenEndOffset++;
        }

        int state = myFlex.yystate();
        if (state == _JavaDocLexer.COMMENT_DATA ||
            state != _JavaDocLexer.INLINE_TAG_LITERAL_TEXT && myTokenEndOffset < myBufferEndOffset && isTagOrMarkupStart(myBuffer.charAt(myTokenEndOffset))) {
          myFlex.yybegin(_JavaDocLexer.COMMENT_DATA_START);
        }

        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = lf ||
                        state == _JavaDocLexer.PARAM_TAG_SPACE ||
                        state == _JavaDocLexer.TAG_DOC_SPACE ||
                        state == _JavaDocLexer.INLINE_TAG_NAME ||
                        state == _JavaDocLexer.DOC_TAG_VALUE_IN_PAREN ||
                        state == _JavaDocLexer.SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON
                        ? JavaDocTokenType.DOC_SPACE
                        : JavaDocTokenType.DOC_COMMENT_DATA;

          return;
        }
      }

      flexLocateToken();
    }

    private static boolean isTagOrMarkupStart(char c) {
      return c == '@' || c == '{' || c == '\"' || c == '<';
    }

    /**
     * @return true for * or *\/
     */
    private boolean detectLeadingAsterisks() {
      return myTokenEndOffset < myBufferEndOffset && myBuffer.charAt(myTokenEndOffset) == '*' &&
             (myTokenEndOffset + 1 >= myBufferEndOffset || myBuffer.charAt(myTokenEndOffset + 1) != '/');
    }

    /**
     * @return The current token is the start of three leading slashes
     */
    private boolean detectLeadingSlashes() {
      return myTokenEndOffset + 2 < myBufferEndOffset && StringUtil.startsWith(myBuffer, myTokenEndOffset, "///");
    }

    private void flexLocateToken() {
      try {
        myState = myFlex.yystate();
        myFlex.goTo(myBufferIndex);
        myTokenType = myFlex.advance();
        myTokenEndOffset = myFlex.getTokenEnd();
      }
      catch (IOException e) {
        // Can't be
      }
    }
  }
}
