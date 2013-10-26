/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.osmorc.manifest.lang;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestLexer extends LexerBase {

  protected CharSequence myBuffer;
  protected int myEndOffset;
  protected int myTokenStart;
  protected int myTokenEnd;
  protected int myCurrentState;
  protected IElementType myTokenType;

  protected static final int INITIAL_STATE = 0;
  protected static final int WAITING_FOR_HEADER_ASSIGNMENT_STATE = 1;
  protected static final int WAITING_FOR_HEADER_ASSIGNMENT_AFTER_BAD_CHARACTER_STATE = 2;
  protected static final int WAITING_FOR_SPACE_AFTER_HEADER_NAME_STATE = 3;

  public ManifestLexer() {
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.myBuffer = buffer;
    this.myEndOffset = endOffset;
    myCurrentState = initialState;

    myTokenStart = startOffset;
    parseNextToken();
  }

  public void advance() {
    myTokenStart = myTokenEnd;
    parseNextToken();
  }

  public int getState() {
    return myCurrentState;
  }

  @Nullable
  public IElementType getTokenType() {
    return myTokenType;
  }

  public int getTokenStart() {
    return myTokenStart;
  }

  public int getTokenEnd() {
    return myTokenEnd;
  }

  public int getBufferEnd() {
    return myEndOffset;
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  protected void parseNextToken() {
    if (myTokenStart < myEndOffset) {
      if (isNewline(myTokenStart)) {
        myTokenType = isLineStart(myTokenStart) ? ManifestTokenType.SECTION_END : ManifestTokenType.NEWLINE;
        myTokenEnd = myTokenStart + 1;
        myCurrentState = INITIAL_STATE;
      }
      else if (myCurrentState == WAITING_FOR_HEADER_ASSIGNMENT_STATE ||
               myCurrentState == WAITING_FOR_HEADER_ASSIGNMENT_AFTER_BAD_CHARACTER_STATE) {
        if (isColon(myTokenStart)) {
          myTokenType = ManifestTokenType.COLON;
          myCurrentState = WAITING_FOR_SPACE_AFTER_HEADER_NAME_STATE;
        }
        else {
          myTokenType = TokenType.BAD_CHARACTER;
          myCurrentState = WAITING_FOR_HEADER_ASSIGNMENT_AFTER_BAD_CHARACTER_STATE;
        }
        myTokenEnd = myTokenStart + 1;
      }
      else if (myCurrentState == WAITING_FOR_SPACE_AFTER_HEADER_NAME_STATE) {
        if (isSpace(myTokenStart)) {
          myTokenType = ManifestTokenType.SIGNIFICANT_SPACE;
        }
        else {
          myTokenType = TokenType.BAD_CHARACTER;
        }
        myCurrentState = INITIAL_STATE;
        myTokenEnd = myTokenStart + 1;
      }
      else if (isHeaderStart(myTokenStart)) {
        if (isAlphaNum(myTokenStart)) {
          myTokenEnd = myTokenStart + 1;
          while (myTokenEnd < myEndOffset && isHeaderChar(myTokenEnd)) {
            myTokenEnd++;
          }
        }
        myTokenType = ManifestTokenType.HEADER_NAME;
        myCurrentState = WAITING_FOR_HEADER_ASSIGNMENT_STATE;
      }
      else if (isContinuationStart(myTokenStart)) {
        myTokenType = ManifestTokenType.SIGNIFICANT_SPACE;
        myTokenEnd = myTokenStart + 1;
        myCurrentState = INITIAL_STATE;
      }
      else if (isSpecialCharacter(myTokenStart)) {
        myTokenType = getToken(myTokenStart);
        myTokenEnd = myTokenStart + getTokenSize(myTokenType);
        myCurrentState = INITIAL_STATE;
      }
      else {
        myTokenEnd = myTokenStart;
        while (myTokenEnd < myEndOffset && !isSpecialCharacter(myTokenEnd) && !isNewline(myTokenEnd)) {
          myTokenEnd++;
        }
        myTokenType = ManifestTokenType.HEADER_VALUE_PART;
      }
    }
    else {
      myTokenType = null;
      myTokenEnd = myTokenStart;
    }
  }

  protected boolean isNewline(int position) {
    return '\n' == myBuffer.charAt(position);
  }

  protected boolean isHeaderStart(int position) {
    return isLineStart(position) && !Character.isWhitespace(myBuffer.charAt(position));
  }

  protected boolean isAlphaNum(int position) {
    return Character.isLetterOrDigit(myBuffer.charAt(position));
  }

  protected boolean isHeaderChar(int position) {
    return isAlphaNum(position) || myBuffer.charAt(position) == '-' || myBuffer.charAt(position) == '_';
  }

  protected boolean isContinuationStart(int position) {
    return isLineStart(position) && !isHeaderStart(position);
  }

  protected boolean isLineStart(int position) {
    return (position == 0 || isNewline(position - 1));
  }

  protected boolean isSpace(int position) {
    return myBuffer.charAt(position) == ' ';
  }

  protected boolean isColon(int position) {
    return myBuffer.charAt(position) == ':';
  }

  protected boolean isSpecialCharacter(int position) {
    return getToken(position) != null;
  }

  protected int getTokenSize(IElementType tokenType) {
    if(tokenType == ManifestTokenType.COLON_EQUALS) {
      return 2;
    }
    else {
      return 1;
    }
  }

  public IElementType getToken(int position) {
    final char c = myBuffer.charAt(position);
    switch (c) {
      case ':':
        if((position + 1) < myEndOffset && getToken(position + 1) == ManifestTokenType.EQUALS) {
         return ManifestTokenType.COLON_EQUALS;
        }
        return ManifestTokenType.COLON;
      case ';':
        return ManifestTokenType.SEMICOLON;
      case ',':
        return ManifestTokenType.COMMA;
      case '=':
        return ManifestTokenType.EQUALS;
      case '(':
        return ManifestTokenType.OPENING_PARENTHESIS_TOKEN;
      case ')':
        return ManifestTokenType.CLOSING_PARENTHESIS_TOKEN;
      case '[':
        return ManifestTokenType.OPENING_BRACKET_TOKEN;
      case ']':
        return ManifestTokenType.CLOSING_BRACKET_TOKEN;
      case '\"':
        return ManifestTokenType.QUOTE;
    }
    return null;
  }
}
