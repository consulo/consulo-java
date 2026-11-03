// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.lexer;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.lexer.LexerBase;
import consulo.util.collection.CharSequenceHashingStrategy;
import consulo.util.collection.Sets;
import consulo.util.collection.primitive.ints.IntStack;
import consulo.util.lang.CharArrayUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.java.language.psi.PsiKeyword.*;

public final class JavaLexer extends LexerBase {
  private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
    ABSTRACT, BOOLEAN, BREAK, BYTE, CASE, CATCH, CHAR, CLASS, CONST, CONTINUE, DEFAULT, DO, DOUBLE, ELSE, EXTENDS, FINAL, FINALLY,
    FLOAT, FOR, GOTO, IF, IMPLEMENTS, IMPORT, INSTANCEOF, INT, INTERFACE, LONG, NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC,
    RETURN, SHORT, STATIC, STRICTFP, SUPER, SWITCH, SYNCHRONIZED, THIS, THROW, THROWS, TRANSIENT, TRY, VOID, VOLATILE, WHILE,
    TRUE, FALSE, NULL, NON_SEALED));

  private static final Set<CharSequence> JAVA9_KEYWORDS = Sets.newHashSet(CharSequenceHashingStrategy.CASE_SENSITIVE);

  static {
    JAVA9_KEYWORDS.addAll(Set.of(OPEN, MODULE, REQUIRES, EXPORTS, OPENS, USES, PROVIDES, TRANSITIVE, TO, WITH));
  }

  public static boolean isKeyword(String id, LanguageLevel level) {
    return KEYWORDS.contains(id) ||
      level.isAtLeast(LanguageLevel.JDK_1_4) && ASSERT.equals(id) ||
      level.isAtLeast(LanguageLevel.JDK_1_5) && ENUM.equals(id);
  }

  public static boolean isSoftKeyword(CharSequence id, LanguageLevel level) {
    return level.isAtLeast(LanguageLevel.JDK_1_9) && JAVA9_KEYWORDS.contains(id) ||
      level.isAtLeast(LanguageLevel.JDK_10) && VAR.contentEquals(id) ||
      level.isAtLeast(LanguageLevel.JDK_16) && RECORD.contentEquals(id) ||
      level.isAtLeast(LanguageLevel.JDK_14) && YIELD.contentEquals(id) ||
      level.isAtLeast(LanguageLevel.JDK_17) && (SEALED.contentEquals(id) || PERMITS.contentEquals(id)) ||
      level.isAtLeast(LanguageLevel.JDK_20) && WHEN.contentEquals(id);
  }

  private static final int STATE_DEFAULT = 0;
  private static final int STATE_TEXT_BLOCK_TEMPLATE = 1;

  private enum LiteralType {
    STRING('"'), CHAR('\''), TEXT_BLOCK('"');

    final char c;

    LiteralType(char c) {
      this.c = c;
    }
  }

  private final _JavaLexer myFlexLexer;
  private final IntStack myStateStack = new IntStack(1);
  private CharSequence myBuffer;
  private char @Nullable [] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private @Nullable IElementType myTokenType;

  /**
   * State at the start of the current token; {@link #getState} must reflect this, not the post-mutation top of {@link #myStateStack}.
   */
  private int myStateBeforeToken;

  /**
   * The length of the last valid unicode escape (6 or greater), or 1 when no unicode escape was found.
   */
  private int mySymbolLength = 1;

  public JavaLexer(LanguageLevel level) {
    myFlexLexer = new _JavaLexer(level);
  }

  @Override
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myBufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);
    myBufferIndex = startOffset;
    myBufferEndOffset = endOffset;
    myTokenType = null;
    myTokenEndOffset = startOffset;
    mySymbolLength = 1;
    myStateStack.clear();
    myStateStack.push(initialState);
    myStateBeforeToken = initialState;
    myFlexLexer.reset(myBuffer, startOffset, endOffset, 0);
  }

  @Override
  public int getState() {
    return myStateBeforeToken;
  }

  @Override
  public @Nullable IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    locateToken();
    return myBufferIndex;
  }

  @Override
  public int getTokenEnd() {
    locateToken();
    return myTokenEndOffset;
  }

  @Override
  public void advance() {
    locateToken();
    myTokenType = null;
  }

  /**
   * Handles whitespace, comment, string literal, text block and string template tokens. Other tokens are handled by calling
   * the flex lexer.
   */
  private void locateToken() {
    if (myTokenType != null) {
      return;
    }

    if (myTokenEndOffset == myBufferEndOffset) {
      myBufferIndex = myBufferEndOffset;
      return;
    }

    myStateBeforeToken = myStateStack.peek();
    myBufferIndex = myTokenEndOffset;

    char c = locateCharAt(myBufferIndex);
    switch (c) {
      //allowed only at the end of files
      case '\u001a' -> {
        if (myBufferIndex + mySymbolLength == myBufferEndOffset) {
          myTokenType = TokenType.WHITE_SPACE;
          myTokenEndOffset = myBufferIndex + mySymbolLength;
        }
        else {
          flexLocateToken();
        }
      }
      case ' ', '\t', '\n', '\r', '\f' -> {
        myTokenType = TokenType.WHITE_SPACE;
        myTokenEndOffset = getWhitespaces(myBufferIndex + mySymbolLength);
      }
      case '{' -> {
        int count1 = myStateStack.peek() >> 16;
        if (count1 > 0) {
          myStateStack.push((myStateStack.pop() & STATE_TEXT_BLOCK_TEMPLATE) | ((count1 + 1) << 16));
        }
        myTokenType = JavaTokenType.LBRACE;
        myTokenEndOffset = myBufferIndex + mySymbolLength;
      }
      case '}' -> {
        int count2 = myStateStack.peek() >> 16;
        boolean updateToken = true;
        if (count2 > 0) {
          if (count2 != 1) {
            myStateStack.push((myStateStack.pop() & STATE_TEXT_BLOCK_TEMPLATE) | ((count2 - 1) << 16));
          }
          else {
            int state = myStateStack.pop();
            if (myStateStack.size() == 0) {
              myStateStack.push(STATE_DEFAULT);
            }
            if ((state & STATE_TEXT_BLOCK_TEMPLATE) != 0) {
              boolean fragment = locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.TEXT_BLOCK);
              myTokenType = fragment ? JavaTokenType.TEXT_BLOCK_TEMPLATE_MID : JavaTokenType.TEXT_BLOCK_TEMPLATE_END;
            }
            else {
              boolean fragment = locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.STRING);
              myTokenType = fragment ? JavaTokenType.STRING_TEMPLATE_MID : JavaTokenType.STRING_TEMPLATE_END;
            }
            updateToken = false;
          }
        }
        if (updateToken) {
          myTokenType = JavaTokenType.RBRACE;
          myTokenEndOffset = myBufferIndex + mySymbolLength;
        }
      }
      case '/' -> {
        if (myBufferIndex + mySymbolLength >= myBufferEndOffset) {
          myTokenType = JavaTokenType.DIV;
          myTokenEndOffset = myBufferEndOffset;
        }
        else {
          int l1 = mySymbolLength;
          char nextChar = locateCharAt(myBufferIndex + l1);
          if (nextChar == '/') {
            int l2 = mySymbolLength;
            if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '/') {
              // Java 23 Markdown comments
              myTokenType = JavaDocElementType.DOC_MARKDOWN_COMMENT;
              myTokenEndOffset = getClosingMarkdownComment(myBufferIndex + l1 + l2);
            }
            else {
              myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
              myTokenEndOffset = getLineTerminator(myBufferIndex + l1 + l2);
            }
          }
          else if (nextChar == '*') {
            int l2 = mySymbolLength;
            if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '*') {
              int l3 = mySymbolLength;
              if (myBufferIndex + l1 + l2 + l3 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2 + l3) == '/') {
                myTokenType = JavaTokenType.C_STYLE_COMMENT;
                myTokenEndOffset = myBufferIndex + l1 + l2 + l3 + mySymbolLength;
              }
              else {
                myTokenType = JavaDocElementType.DOC_COMMENT;
                myTokenEndOffset = getClosingComment(myBufferIndex + l1 + l2 + l3);
              }
            }
            else {
              myTokenType = JavaTokenType.C_STYLE_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + l1 + l2 + mySymbolLength);
            }
          }
          else {
            flexLocateToken();
          }
        }
      }
      case '#' -> {
        if (myBufferIndex == 0 && mySymbolLength == 1 && myBufferEndOffset > 1 && charAt(1) == '!') {
          myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
          myTokenEndOffset = getLineTerminator(2);
        }
        else {
          flexLocateToken();
        }
      }
      case '\'' -> {
        myTokenType = JavaTokenType.CHARACTER_LITERAL;
        locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.CHAR);
      }
      case '"' -> {
        int l1 = mySymbolLength;
        if (myBufferIndex + l1 < myBufferEndOffset && locateCharAt(myBufferIndex + l1) == '"') {
          int l2 = mySymbolLength;
          if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '"') {
            boolean fragment = locateLiteralEnd(myBufferIndex + l1 + l2 + mySymbolLength, LiteralType.TEXT_BLOCK);
            myTokenType = fragment ? JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN : JavaTokenType.TEXT_BLOCK_LITERAL;
          }
          else {
            myTokenType = JavaTokenType.STRING_LITERAL;
            myTokenEndOffset = myBufferIndex + l1 + l2;
          }
        }
        else {
          boolean fragment = locateLiteralEnd(myBufferIndex + l1, LiteralType.STRING);
          myTokenType = fragment ? JavaTokenType.STRING_TEMPLATE_BEGIN : JavaTokenType.STRING_LITERAL;
        }
      }
      default -> flexLocateToken();
    }

    if (myTokenEndOffset > myBufferEndOffset) {
      myTokenEndOffset = myBufferEndOffset;
    }
  }

  private int getWhitespaces(int offset) {
    return getChars(offset, " \t\n\r\f", '\u001a');
  }

  private int getSimpleWhitespaces(int offset) {
    return getChars(offset, " \t", null);
  }

  /**
   * @param offset        the offset to start.
   * @param charsToDetect the chars to detect.
   * @param endChar       the char which is applied only at the end of the file, or {@code null} if no end sequence is needed.
   * @return The new position if none of the chars were detected
   */
  private int getChars(int offset, CharSequence charsToDetect, @Nullable Character endChar) {
    int pos = offset;
    while (pos < myBufferEndOffset) {
      boolean detected = false;
      char c = locateCharAt(pos);
      for (int i = 0; i < charsToDetect.length(); i++) {
        if (charsToDetect.charAt(i) == c) {
          pos += mySymbolLength;
          detected = true;
          break;
        }
      }
      if (!detected && endChar != null && pos + mySymbolLength == myBufferEndOffset) {
        if (endChar == c) {
          pos += mySymbolLength;
          break;
        }
      }
      if (!detected) {
        break;
      }
    }

    return pos;
  }

  private void flexLocateToken() {
    try {
      myFlexLexer.goTo(myBufferIndex);
      myTokenType = myFlexLexer.advance();
      myTokenEndOffset = myFlexLexer.getTokenEnd();
    }
    catch (IOException e) { /* impossible */ }
  }

  /**
   * @param offset      the offset to start.
   * @param literalType the type of string literal.
   * @return {@code true} if this is a string template fragment, {@code false} otherwise.
   */
  private boolean locateLiteralEnd(int offset, LiteralType literalType) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = locateCharAt(pos);

      if (c == '\\') {
        pos += mySymbolLength;
        // on (encoded) backslash we also need to skip the next symbol (e.g. \\u005c" is translated to \")
        if (pos < myBufferEndOffset) {
          if (locateCharAt(pos) == '{' && literalType != LiteralType.CHAR) {
            pos += mySymbolLength;
            myTokenEndOffset = pos;
            if (myStateStack.peek() == 0) {
              myStateStack.pop();
            }
            if (literalType == LiteralType.TEXT_BLOCK) {
              myStateStack.push(STATE_TEXT_BLOCK_TEMPLATE | (1 << 16));
            }
            else {
              myStateStack.push(STATE_DEFAULT | (1 << 16));
            }
            return true;
          }
        }
      }
      else if (c == literalType.c) {
        if (literalType == LiteralType.TEXT_BLOCK) {
          if ((pos += mySymbolLength) < myBufferEndOffset && locateCharAt(pos) == '"') {
            if ((pos += mySymbolLength) < myBufferEndOffset && locateCharAt(pos) == '"') {
              myTokenEndOffset = pos + mySymbolLength;
              return false;
            }
          }
          continue;
        }
        else {
          myTokenEndOffset = pos + mySymbolLength;
          return false;
        }
      }
      else if ((c == '\n' || c == '\r') && mySymbolLength == 1 && literalType != LiteralType.TEXT_BLOCK) {
        myTokenEndOffset = pos;
        return false;
      }
      pos += mySymbolLength;
    }
    myTokenEndOffset = pos;
    return false;
  }

  private int getClosingMarkdownComment(int offset) {
    int pos = offset;
    while (pos < myBufferEndOffset) {
      pos = getLineTerminator(pos);
      // Account for whitespaces beforehand
      int newPos = getSimpleWhitespaces(pos + mySymbolLength);

      newPos = getCharSeqAt(newPos, "///");
      if (newPos == -1) {
        break;
      }
      pos = newPos;
    }

    return pos;
  }

  private int getClosingComment(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = locateCharAt(pos);
      pos += mySymbolLength;
      if (c == '*' && pos < myBufferEndOffset && locateCharAt(pos) == '/') {
        break;
      }
    }

    return pos + mySymbolLength;
  }

  private int getLineTerminator(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = locateCharAt(pos);
      if (c == '\r' || c == '\n') {
        break;
      }
      pos += mySymbolLength;
    }

    return pos;
  }

  /**
   * @return The position after of the last char, -1 otherwise
   */
  @SuppressWarnings("SameParameterValue")
  private int getCharSeqAt(int offset, CharSequence charSequence) {
    int pos = offset;
    for (int i = 0; i < charSequence.length(); i++) {
      if (!isLocatedCharAt(pos, charSequence.charAt(i))) {
        return -1;
      }
      pos += mySymbolLength;
    }
    return pos;
  }

  private boolean isLocatedCharAt(int offset, char charToDetect) {
    return (offset < myBufferEndOffset) && locateCharAt(offset) == charToDetect;
  }

  private char charAt(int offset) {
    char[] bufferArray = myBufferArray;
    return bufferArray != null ? bufferArray[offset] : myBuffer.charAt(offset);
  }

  private char locateCharAt(int offset) {
    mySymbolLength = 1;
    char first = charAt(offset);
    if (first != '\\') {
      return first;
    }
    int pos = offset + 1;
    if (pos < myBufferEndOffset && charAt(pos) == '\\') {
      return first;
    }
    boolean escaped = true;
    int i = offset;
    while (--i >= 0 && charAt(i) == '\\') {
      escaped = !escaped;
    }
    if (!escaped) {
      return first;
    }
    if (pos < myBufferEndOffset && charAt(pos) != 'u') {
      return first;
    }
    //noinspection StatementWithEmptyBody
    while (++pos < myBufferEndOffset && charAt(pos) == 'u') ;
    if (pos + 3 >= myBufferEndOffset) {
      return first;
    }
    int result = 0;
    int max = pos + 4;
    while (pos < max) {
      result <<= 4;
      char c = charAt(pos);
      if ('0' <= c && c <= '9') {
        result += c - '0';
      }
      else if ('a' <= c && c <= 'f') {
        result += (c - 'a') + 10;
      }
      else if ('A' <= c && c <= 'F') {
        result += (c - 'A') + 10;
      }
      else {
        return first;
      }
      pos++;
    }
    mySymbolLength = pos - offset;
    return (char)result;
  }

  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEndOffset;
  }
}
