/*
 * Copyright 2013 Consulo.org
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
package com.intellij.java.language.impl.psi.impl.source.tree;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocTokenImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiIdentifierImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiKeywordImpl;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.tree.java.IJavaDocElementType;
import com.intellij.java.language.psi.tree.java.IJavaElementType;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.ASTLeafFactory;
import consulo.language.impl.ast.LeafElement;
import consulo.language.impl.psi.PsiCoreCommentImpl;
import consulo.language.version.LanguageVersion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 */
public class CoreJavaASTLeafFactory implements Constants, ASTLeafFactory {
  @Override
  @Nonnull
  public LeafElement createLeaf(@Nonnull final IElementType type, @Nonnull LanguageVersion languageVersion, @Nonnull final CharSequence text) {
    if (type == JavaTokenType.C_STYLE_COMMENT || type == JavaTokenType.END_OF_LINE_COMMENT) {
      return new PsiCoreCommentImpl(type, text);
    } else if (type == JavaTokenType.IDENTIFIER) {
      return new PsiIdentifierImpl(text);
    } else if (ElementType.KEYWORD_BIT_SET.contains(type)) {
      return new PsiKeywordImpl(type, text);
    } else if (type instanceof IJavaElementType) {
      return new PsiJavaTokenImpl(type, text);
    } else if (type instanceof IJavaDocElementType) {
      assert type != DOC_TAG_VALUE_ELEMENT;
      return new PsiDocTokenImpl(type, text);
    }

    return null;
  }

  @Override
  public boolean test(@Nullable IElementType input) {
    return input == JavaTokenType.C_STYLE_COMMENT ||
        input == JavaTokenType.END_OF_LINE_COMMENT ||
        input == JavaTokenType.IDENTIFIER ||
        ElementType.KEYWORD_BIT_SET.contains(input) ||
        input instanceof IJavaElementType || input instanceof IJavaDocElementType;
  }
}
