/* It's an automatically generated code. Do not modify it. */
package com.intellij.java.language.lexer;

import com.intellij.java.language.psi.JavaDocTokenType;
import consulo.language.ast.IElementType;
import consulo.language.lexer.FlexLexer;

@SuppressWarnings("ALL")
%%

%{
  private boolean myJdk1_5Enabled;
  /** Nesting level of braces of inline tags having literal text */
  private int myLiteralTextContentBracesLevel = 0;
  /* Enable markdown support for java 23 */
  private boolean myMarkdownMode = false;
  /** Whether comment data should take into account spaces, on used with {@link #myMarkdownMode} */
  private boolean commentDataWithSpaces = false;
  /** Whether we're in @value tag and should accept VALUE_FORMAT */
  private boolean inValueTag = false;
  /** Nesting level of generic types `<T>` (Eg. `List<List<List>>`) Only used in the DOG_TAG outside of parentheses */
  private int genericNestingLevel = 0;

  public _JavaDocLexer(boolean isJdk1_5Enabled) {
    this((java.io.Reader)null);
    myJdk1_5Enabled = isJdk1_5Enabled;
  }

  /** Should be called right after the usual jflex reset function */
  public void _reset(boolean isMarkdownModeEnabled) {
    myMarkdownMode = isMarkdownModeEnabled;
    // Reset the internal states of the lexer.
    // Such behavior relies on the lexer only lexing the entire comment at once
    myLiteralTextContentBracesLevel = 0;
    commentDataWithSpaces = false;
    inValueTag = false;
    genericNestingLevel = 0;
  }

  public boolean checkAhead(char c) {
    if (zzMarkedPos >= zzBuffer.length()) return false;
    return zzBuffer.charAt(zzMarkedPos) == c;
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzAtEOF = false;
  }
%}

%public
%class _JavaDocLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType


%state COMMENT_DATA_START
%state COMMENT_DATA
%state TAG_DOC_SPACE
%state PARAM_TAG_SPACE
%state DOC_TAG_VALUE
%state DOC_TAG_VALUE_IN_PAREN
%state DOC_TAG_VALUE_IN_LTGT
%state INLINE_TAG_NAME
%state SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON
%state INLINE_TAG_LITERAL_SPACE
%state INLINE_TAG_LITERAL_TEXT
%state SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES
%state SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES

WHITE_DOC_SPACE_CHAR=[\ \t\f\n\r]
WHITE_DOC_SPACE_NO_LR=[\ \t\f]
DIGIT=[0-9]
ALPHA=[:jletter:]
IDENTIFIER_WITHOUT_SYMBOLS={ALPHA}({ALPHA}|{DIGIT})*
IDENTIFIER={ALPHA}({ALPHA}|{DIGIT}|[":.-"])*
TAG_IDENTIFIER=[^\ \t\f\n\r]+
INLINE_TAG_IDENTIFIER=[^\ \t\f\n\r\}]+
FORMAT_STRING=%[^\ \t\f\n\r}]+

// 20 should cover most fences. JFlex doesn't allow unlimited upper bound
CODE_FENCE_BACKTICKS=(`{3, 20})
CODE_FENCE_WAVES=(\~{3, 20})
CODE_FENCE = ({CODE_FENCE_BACKTICKS}|{CODE_FENCE_WAVES})

START_COMMENT_HTML="/**"
LEADING_TOKEN_HTML=\*
LEADING_TOKEN_MARKDOWN="///"


%%

<YYINITIAL> {START_COMMENT_HTML} {
        if (myMarkdownMode) {
          return JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER;
        }
        yybegin(COMMENT_DATA_START);
        return JavaDocTokenType.DOC_COMMENT_START;
}
<YYINITIAL> {LEADING_TOKEN_MARKDOWN} {
        if(myMarkdownMode) {
          yybegin(COMMENT_DATA_START);
          return JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
        }
        return JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER;
}

<COMMENT_DATA_START> {WHITE_DOC_SPACE_CHAR}+ { return JavaDocTokenType.DOC_SPACE; }
<COMMENT_DATA> {WHITE_DOC_SPACE_NO_LR}+ { return commentDataWithSpaces ? JavaDocTokenType.DOC_SPACE : JavaDocTokenType.DOC_COMMENT_DATA; }
<COMMENT_DATA> [\n\r]+{WHITE_DOC_SPACE_CHAR}* { return JavaDocTokenType.DOC_SPACE; }

<DOC_TAG_VALUE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_SPACE; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> ({ALPHA}|[_0-9\."$"\[\]"-"])+ { return JavaDocTokenType.DOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE> [\(] { yybegin(DOC_TAG_VALUE_IN_PAREN); return JavaDocTokenType.DOC_TAG_VALUE_LPAREN; }
<DOC_TAG_VALUE_IN_PAREN> [\)] { yybegin(DOC_TAG_VALUE); return JavaDocTokenType.DOC_TAG_VALUE_RPAREN; }
<DOC_TAG_VALUE> [/] { return JavaDocTokenType.DOC_TAG_VALUE_SLASH; }
<DOC_TAG_VALUE> "##" { return JavaDocTokenType.DOC_TAG_VALUE_DOUBLE_SHARP_TOKEN; }
<DOC_TAG_VALUE> [#] { return JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN, DOC_TAG_VALUE_IN_LTGT> [,] { return JavaDocTokenType.DOC_TAG_VALUE_COMMA; }
<DOC_TAG_VALUE_IN_PAREN> {WHITE_DOC_SPACE_CHAR}+ { return JavaDocTokenType.DOC_SPACE; }

// Allowed since JDK 15
<DOC_TAG_VALUE_IN_PAREN> [\<] { return JavaDocTokenType.DOC_TAG_VALUE_LT; }
<DOC_TAG_VALUE_IN_PAREN> [\>] { return JavaDocTokenType.DOC_TAG_VALUE_GT; }

<INLINE_TAG_NAME, COMMENT_DATA_START> "@param" { yybegin(PARAM_TAG_SPACE); return JavaDocTokenType.DOC_TAG_NAME; }
<PARAM_TAG_SPACE> {WHITE_DOC_SPACE_CHAR}+ {yybegin(DOC_TAG_VALUE); return JavaDocTokenType.DOC_SPACE; }
<DOC_TAG_VALUE> [\<] {
  if (myJdk1_5Enabled) {
    yybegin(DOC_TAG_VALUE_IN_LTGT);
    genericNestingLevel += 1;
    return JavaDocTokenType.DOC_TAG_VALUE_LT;
  }
  else {
    yybegin(COMMENT_DATA);
    return JavaDocTokenType.DOC_COMMENT_DATA;
  }
}
<DOC_TAG_VALUE_IN_LTGT> [\<] {
        genericNestingLevel += 1;
        return JavaDocTokenType.DOC_TAG_VALUE_LT;
      }
<DOC_TAG_VALUE_IN_LTGT> ({IDENTIFIER}|\?) { return JavaDocTokenType.DOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE_IN_LTGT> [\>] {
        genericNestingLevel -= 1;
        if (genericNestingLevel <= 0) {
          yybegin(COMMENT_DATA);
        }
        return JavaDocTokenType.DOC_TAG_VALUE_GT;
      }
<DOC_TAG_VALUE_IN_LTGT> [\ ] {
        return JavaDocTokenType.DOC_SPACE;
      }

<COMMENT_DATA_START, COMMENT_DATA> {
      {CODE_FENCE} {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocTokenType.DOC_CODE_FENCE;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
      }

      // According to the JFlex user guide, lookahead should be avoided.
      (\\\\) { yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_COMMENT_DATA; }
      (\\\[) { yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_COMMENT_DATA; }
      (\\\]) { yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_COMMENT_DATA; }
      (\\\() { yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_COMMENT_DATA; }
      (\\\)) { yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_COMMENT_DATA; }
      (\\\`) { yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_COMMENT_DATA; }

      [,] {
          yybegin(COMMENT_DATA);
          if(myMarkdownMode) {
            return JavaDocTokenType.DOC_COMMA;
          }
          return JavaDocTokenType.DOC_COMMENT_DATA;
      }

      "`" {
         yybegin(COMMENT_DATA);
          if(myMarkdownMode) {
            return JavaDocTokenType.DOC_INLINE_CODE_FENCE;
          }
          return JavaDocTokenType.DOC_COMMENT_DATA;
      }

      "#" {
         yybegin(COMMENT_DATA);
         if(myMarkdownMode) {
            return JavaDocTokenType.DOC_SHARP;
         }
         return JavaDocTokenType.DOC_COMMENT_DATA;
      }

      "##" {
         yybegin(COMMENT_DATA);
         if(myMarkdownMode) {
           return JavaDocTokenType.DOC_DOUBLE_SHARP;
         }
         return JavaDocTokenType.DOC_COMMENT_DATA;
      }

      [/] {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocTokenType.DOC_TAG_VALUE_SLASH;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
      }

      \[ {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          commentDataWithSpaces = true;
          return JavaDocTokenType.DOC_LBRACKET;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
      }
      \] {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          commentDataWithSpaces = false;
          return JavaDocTokenType.DOC_RBRACKET;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
      }
      \( {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocTokenType.DOC_LPAREN;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
      }
      \) {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocTokenType.DOC_RPAREN;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
       }

      [\<] {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocTokenType.DOC_LT;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
      }

      [\>] {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocTokenType.DOC_GT;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
      }
}

<COMMENT_DATA_START, COMMENT_DATA> "{" {
  if (checkAhead('@')) {
    yybegin(INLINE_TAG_NAME);
    return JavaDocTokenType.DOC_INLINE_TAG_START;
  }
  else {
    yybegin(COMMENT_DATA);
    return JavaDocTokenType.DOC_INLINE_TAG_START;
  }
}

<INLINE_TAG_NAME> "@"("code"|"literal") { yybegin(INLINE_TAG_LITERAL_SPACE); return JavaDocTokenType.DOC_TAG_NAME; }
<INLINE_TAG_NAME> "@"("return"|"summary") { yybegin(DOC_TAG_VALUE); return JavaDocTokenType.DOC_TAG_NAME; }
<INLINE_TAG_NAME> "@snippet" { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocTokenType.DOC_TAG_NAME; }
<INLINE_TAG_NAME> "@value" { inValueTag = true; yybegin(TAG_DOC_SPACE); return JavaDocTokenType.DOC_TAG_NAME; }
<INLINE_TAG_NAME> "@"{INLINE_TAG_IDENTIFIER} { yybegin(TAG_DOC_SPACE); return JavaDocTokenType.DOC_TAG_NAME; }
<COMMENT_DATA_START, COMMENT_DATA, TAG_DOC_SPACE, DOC_TAG_VALUE, INLINE_TAG_LITERAL_SPACE, SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES,
SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES, SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON, DOC_TAG_VALUE_IN_LTGT, INLINE_TAG_NAME> "}" { inValueTag = false; yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_INLINE_TAG_END; }

<INLINE_TAG_LITERAL_SPACE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(INLINE_TAG_LITERAL_TEXT); return JavaDocTokenType.DOC_SPACE; }
<INLINE_TAG_LITERAL_SPACE> . { yybegin(INLINE_TAG_LITERAL_TEXT); return JavaDocTokenType.DOC_COMMENT_DATA; }

<COMMENT_DATA_START, COMMENT_DATA, DOC_TAG_VALUE> . { yybegin(COMMENT_DATA); return JavaDocTokenType.DOC_COMMENT_DATA; }
<COMMENT_DATA_START> "@"("author"|"deprecated"|"hidden"|"index"|"implNote"|"return"|"serial") { yybegin(DOC_TAG_VALUE); return JavaDocTokenType.DOC_TAG_NAME; }
<COMMENT_DATA_START> "@"{TAG_IDENTIFIER} { yybegin(TAG_DOC_SPACE); return JavaDocTokenType.DOC_TAG_NAME; }

<SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES> {
      {WHITE_DOC_SPACE_CHAR}+ { return JavaDocTokenType.DOC_SPACE; }
      [^\n\"]+ { return JavaDocTokenType.DOC_TAG_VALUE_TOKEN; }
      \" { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocTokenType.DOC_TAG_VALUE_QUOTE; }
}

<SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES> {
      {WHITE_DOC_SPACE_CHAR}+ { return JavaDocTokenType.DOC_SPACE; }
      [^\n']+ { return JavaDocTokenType.DOC_TAG_VALUE_TOKEN; }
      ' { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocTokenType.DOC_TAG_VALUE_QUOTE; }
}

<SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON> {
      {IDENTIFIER_WITHOUT_SYMBOLS} { return JavaDocTokenType.DOC_TAG_VALUE_TOKEN; }
      = { return JavaDocTokenType.DOC_TAG_VALUE_TOKEN; }
      \" { yybegin(SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES); return JavaDocTokenType.DOC_TAG_VALUE_QUOTE; }
      ' { yybegin(SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES); return JavaDocTokenType.DOC_TAG_VALUE_QUOTE; }
      : { yybegin(INLINE_TAG_LITERAL_TEXT); return JavaDocTokenType.DOC_TAG_VALUE_COLON; }

      {LEADING_TOKEN_HTML} {
        if (myMarkdownMode) {
          return JavaDocTokenType.DOC_COMMENT_DATA;
        }
        return JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
      }
      {LEADING_TOKEN_MARKDOWN} {
        if (myMarkdownMode) {
          return JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
        }
        return JavaDocTokenType.DOC_COMMENT_DATA;
      }

      {WHITE_DOC_SPACE_CHAR}+ { return JavaDocTokenType.DOC_SPACE; }

      [^\*] { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocTokenType.DOC_COMMENT_DATA; }
      [^\/\/\/] { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocTokenType.DOC_COMMENT_DATA; }
}

<INLINE_TAG_LITERAL_TEXT> {
      \} {
        if (myLiteralTextContentBracesLevel > 0) {
          myLiteralTextContentBracesLevel--;
          return JavaDocTokenType.DOC_COMMENT_DATA;
        } else {
          yybegin(COMMENT_DATA);
          return JavaDocTokenType.DOC_INLINE_TAG_END;
        }
      }
      \{ { myLiteralTextContentBracesLevel++; return JavaDocTokenType.DOC_COMMENT_DATA; }
      {WHITE_DOC_SPACE_CHAR}+ { return JavaDocTokenType.DOC_SPACE; }
      [^{}\n]+ {return JavaDocTokenType.DOC_COMMENT_DATA; }
}

<DOC_TAG_VALUE> {FORMAT_STRING} {
  if (inValueTag) {
    inValueTag = false;
    yybegin(TAG_DOC_SPACE);
    return JavaDocTokenType.DOC_TAG_FORMAT_STRING;
  }
  else {
    yypushback(yylength());
    yybegin(COMMENT_DATA);
    return JavaDocTokenType.DOC_COMMENT_DATA;
  }
}

<TAG_DOC_SPACE> {WHITE_DOC_SPACE_CHAR}+ {
  if (checkAhead('<') || checkAhead('\"')) yybegin(COMMENT_DATA);
  else if (checkAhead('\u007b')) yybegin(COMMENT_DATA);  // lbrace - there's a error in JLex when typing lbrace directly
  else yybegin(DOC_TAG_VALUE);
  return JavaDocTokenType.DOC_SPACE;
}

"*"+"/" {
    if(myMarkdownMode) {
      return JavaDocTokenType.DOC_COMMENT_DATA;
    }
    return JavaDocTokenType.DOC_COMMENT_END;
}
[^] { return JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER; }
