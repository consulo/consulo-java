/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.search;

import com.intellij.java.impl.psi.impl.source.tree.StdTokenSets;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.lexer.Lexer;
import consulo.language.psi.PsiFile;
import consulo.language.psi.search.IndexPatternBuilder;
import consulo.xml.psi.xml.XmlElementType;
import consulo.xml.psi.xml.XmlTokenType;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet XML_DATA_CHARS = TokenSet.create(XmlTokenType.XML_DATA_CHARACTERS);
  public static final TokenSet XML_COMMENT_BIT_SET = TokenSet.create(XmlElementType.XML_COMMENT_CHARACTERS);

  @Override
  @Nullable
  public Lexer getIndexingLexer(PsiFile file) {
    if (file instanceof PsiJavaFile /*&& !(file instanceof JspFile)*/) {
      return new JavaLexer(((PsiJavaFile) file).getLanguageLevel());
    }
    return null;
  }

  @Override
  @Nullable
  public TokenSet getCommentTokenSet(PsiFile file) {
    if (file instanceof PsiJavaFile /*&& !(file instanceof JspFile)*/) {
      return TokenSet.orSet(StdTokenSets.COMMENT_BIT_SET, XML_COMMENT_BIT_SET, JavaDocTokenType.ALL_JAVADOC_TOKENS, XML_DATA_CHARS);
    }
    return null;
  }

  @Override
  public int getCommentStartDelta(IElementType tokenType) {
    return 0;
  }

  @Override
  public int getCommentEndDelta(IElementType tokenType) {
    return tokenType == JavaTokenType.C_STYLE_COMMENT ? "*/".length() : 0;
  }
}
