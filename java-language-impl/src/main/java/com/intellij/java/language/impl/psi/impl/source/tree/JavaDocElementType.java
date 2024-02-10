/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.impl.parser.JavaParserUtil;
import com.intellij.java.language.impl.parser.JavadocParser;
import com.intellij.java.language.impl.psi.impl.source.javadoc.*;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import com.intellij.java.language.psi.tree.ParentProviderElementType;
import com.intellij.java.language.psi.tree.java.IJavaDocElementType;
import consulo.language.Language;
import consulo.language.ast.*;
import consulo.language.impl.psi.LazyParseablePsiElement;
import consulo.language.lexer.Lexer;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

public interface JavaDocElementType {
  @SuppressWarnings("deprecation")
  class JavaDocCompositeElementType extends IJavaDocElementType implements ICompositeElementType {
    private final Supplier<ASTNode> myFactory;

    private JavaDocCompositeElementType(@NonNls final String debugName, final Supplier<ASTNode> factory) {
      super(debugName);
      myFactory = factory;
    }

    @Nonnull
    @Override
    public ASTNode createCompositeNode() {
      return myFactory.get();
    }
  }

  class JavaDocLazyElementType extends ILazyParseableElementType {
    private JavaDocLazyElementType(@NonNls final String debugName) {
      super(debugName, JavaLanguage.INSTANCE);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }
  }

  final class JavaDocParentProviderElementType extends IJavaDocElementType implements ParentProviderElementType {

    private final Set<IElementType> myParentElementTypes;

    public JavaDocParentProviderElementType(@Nonnull String debugName, @Nonnull IElementType parentElementType) {
      super(debugName);
      myParentElementTypes = Collections.singleton(parentElementType);
    }

    @Override
    public
    @Nonnull
    Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }


  IElementType DOC_TAG = new JavaDocCompositeElementType("DOC_TAG", PsiDocTagImpl::new);
  IElementType DOC_INLINE_TAG = new JavaDocCompositeElementType("DOC_INLINE_TAG", PsiInlineDocTagImpl::new);
  IElementType DOC_METHOD_OR_FIELD_REF = new JavaDocCompositeElementType("DOC_METHOD_OR_FIELD_REF", PsiDocMethodOrFieldRef::new);
  IElementType DOC_PARAMETER_REF = new JavaDocCompositeElementType("DOC_PARAMETER_REF", PsiDocParamRef::new);
  IElementType DOC_TAG_VALUE_ELEMENT = new IJavaDocElementType("DOC_TAG_VALUE_ELEMENT");

  IElementType DOC_SNIPPET_TAG =
    new JavaDocCompositeElementType("DOC_SNIPPET_TAG", PsiSnippetDocTagImpl::new);
  IElementType DOC_SNIPPET_TAG_VALUE = new JavaDocCompositeElementType("DOC_SNIPPET_TAG_VALUE",
                                                                       PsiSnippetDocTagValueImpl::new);
  IElementType DOC_SNIPPET_BODY = new JavaDocCompositeElementType("DOC_SNIPPET_BODY",
                                                                  PsiSnippetDocTagBodyImpl::new);
  IElementType DOC_SNIPPET_ATTRIBUTE = new JavaDocCompositeElementType("DOC_SNIPPET_ATTRIBUTE",
                                                                       PsiSnippetAttributeImpl::new);
  IElementType DOC_SNIPPET_ATTRIBUTE_LIST =
    new JavaDocCompositeElementType("DOC_SNIPPET_ATTRIBUTE_LIST",
                                    PsiSnippetAttributeListImpl::new);
  IElementType DOC_SNIPPET_ATTRIBUTE_VALUE =
    new JavaDocParentProviderElementType("DOC_SNIPPET_ATTRIBUTE_VALUE", new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE_VALUE"));

  ILazyParseableElementType DOC_REFERENCE_HOLDER = new JavaDocLazyElementType("DOC_REFERENCE_HOLDER") {
    private final JavaParserUtil.ParserWrapper myParser = JavadocParser::parseJavadocReference;

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser, false, LanguageLevel.JDK_1_3);
    }
  };

  ILazyParseableElementType DOC_TYPE_HOLDER = new JavaDocLazyElementType("DOC_TYPE_HOLDER") {
    private final JavaParserUtil.ParserWrapper myParser = JavadocParser::parseJavadocType;

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser, false, LanguageLevel.JDK_1_3);
    }
  };

  ILazyParseableElementType DOC_COMMENT = new IReparseableElementType("DOC_COMMENT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = JavadocParser::parseDocCommentText;

    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text);
    }

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }

    @Override
    public boolean isParsable(@Nullable ASTNode parent,
                              @Nonnull CharSequence buffer,
                              @Nonnull Language fileLanguage,
                              @Nonnull Project project) {
      if (!StringUtil.startsWith(buffer, "/**") || !StringUtil.endsWith(buffer, "*/")) {
        return false;
      }

      PsiFile psiFile = parent.getPsi().getContainingFile();
      final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(psiFile);
      LanguageLevel languageLevel =
        moduleForPsiElement == null ? LanguageLevel.HIGHEST : EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(moduleForPsiElement);

      Lexer lexer = new JavaLexer(languageLevel);
      lexer.start(buffer);
      if (lexer.getTokenType() == DOC_COMMENT) {
        lexer.advance();
        if (lexer.getTokenType() == null) {
          return true;
        }
      }
      return false;
    }
  };

  TokenSet ALL_JAVADOC_ELEMENTS = TokenSet.create(DOC_TAG,
                                                  DOC_INLINE_TAG,
                                                  DOC_METHOD_OR_FIELD_REF,
                                                  DOC_PARAMETER_REF,
                                                  DOC_TAG_VALUE_ELEMENT,
                                                  DOC_REFERENCE_HOLDER,
                                                  DOC_TYPE_HOLDER,
                                                  DOC_COMMENT);
}