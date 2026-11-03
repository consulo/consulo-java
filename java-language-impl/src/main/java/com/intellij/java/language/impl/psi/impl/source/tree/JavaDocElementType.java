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
import com.intellij.java.language.impl.lexer.JavaDocLexer;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.impl.lexer.JavaTypeEscapeLexer;
import com.intellij.java.language.impl.parser.JavaDocParser;
import com.intellij.java.language.impl.parser.JavaParser;
import com.intellij.java.language.impl.parser.JavaParserUtil;
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
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

public interface JavaDocElementType {
  @SuppressWarnings("deprecation")
  class JavaDocCompositeElementType extends IJavaDocElementType implements ICompositeElementType {
    private final Supplier<ASTNode> myFactory;

    private JavaDocCompositeElementType(final String debugName, final Supplier<ASTNode> factory) {
      super(debugName);
      myFactory = factory;
    }

    @Override
    public ASTNode createCompositeNode() {
      return myFactory.get();
    }
  }

  class JavaDocLazyElementType extends ILazyParseableElementType {
    private JavaDocLazyElementType(final String debugName) {
      super(debugName, JavaLanguage.INSTANCE);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }
  }

  final class JavaDocParentProviderElementType extends IJavaDocElementType implements ParentProviderElementType {

    private final Set<IElementType> myParentElementTypes;

    public JavaDocParentProviderElementType(String debugName, IElementType parentElementType) {
      super(debugName);
      myParentElementTypes = Collections.singleton(parentElementType);
    }

    @Override
    public
    Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }


  IElementType DOC_TAG = new JavaDocCompositeElementType("DOC_TAG", PsiDocTagImpl::new);
  IElementType DOC_INLINE_TAG = new JavaDocCompositeElementType("DOC_INLINE_TAG", PsiInlineDocTagImpl::new);
  IElementType DOC_METHOD_OR_FIELD_REF = new JavaDocCompositeElementType("DOC_METHOD_OR_FIELD_REF", PsiDocMethodOrFieldRef::new);
  IElementType DOC_FRAGMENT_REF = new JavaDocCompositeElementType("DOC_FRAGMENT_REF", PsiDocFragmentRefImpl::new);
  IElementType DOC_FRAGMENT_NAME = new JavaDocCompositeElementType("DOC_FRAGMENT_NAME", PsiDocFragmentNameImpl::new);
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
  IElementType DOC_MARKDOWN_CODE_BLOCK = new JavaDocCompositeElementType("DOC_CODE_BLOCK", PsiMarkdownCodeBlockImpl::new);
  IElementType DOC_MARKDOWN_REFERENCE_LINK = new JavaDocCompositeElementType("DOC_REFERENCE_LINK", PsiMarkdownReferenceLinkImpl::new);
  IElementType DOC_MARKDOWN_REFERENCE_LABEL = new JavaDocCompositeElementType("DOC_REFERENCE_LABEL", PsiMarkdownReferenceLabelImpl::new);
  IElementType DOC_MARKDOWN_LINK = new JavaDocCompositeElementType("DOC_MARKDOWN_LINK", PsiMarkdownLinkImpl::new);

  ILazyParseableElementType DOC_REFERENCE_HOLDER = new DocReferenceHolderElementType();
  ILazyParseableElementType DOC_TYPE_HOLDER = new DocTypeHolderElementType();
  ILazyParseableElementType DOC_COMMENT = new DocCommentElementType("DOC_COMMENT") {
    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text);
    }
  };
  ILazyParseableElementType DOC_MARKDOWN_COMMENT = new DocCommentElementType("DOC_MARKDOWN_COMMENT") {
    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text, true);
    }
  };

  /**
   * TokenSet for both {@link #DOC_COMMENT classic} and {@link #DOC_MARKDOWN_COMMENT Markdown} Javadoc comments types
   */
  TokenSet DOC_COMMENT_TOKENS = TokenSet.create(DOC_COMMENT, DOC_MARKDOWN_COMMENT);

  TokenSet ALL_JAVADOC_ELEMENTS = TokenSet.create(DOC_TAG,
                                                  DOC_INLINE_TAG,
                                                  DOC_METHOD_OR_FIELD_REF,
                                                  DOC_PARAMETER_REF,
                                                  DOC_TAG_VALUE_ELEMENT,
                                                  DOC_REFERENCE_HOLDER,
                                                  DOC_TYPE_HOLDER,
                                                  DOC_COMMENT,
                                                  DOC_MARKDOWN_COMMENT);

  final class DocReferenceHolderElementType extends JavaDocLazyElementType {
    public DocReferenceHolderElementType() {
      super("DOC_REFERENCE_HOLDER");
    }

    @Override
    public @Nullable ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(
        chameleon,
        builder -> new JavaDocParser(builder, JavaParserUtil.getLanguageLevel(builder)).parseJavadocReference(JavaParser.INSTANCE),
        false,
        LanguageLevel.JDK_1_3);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new PsiDocReferenceHolderImpl(text);
    }
  }

  final class DocTypeHolderElementType extends JavaDocLazyElementType {
    public DocTypeHolderElementType() {
      super("DOC_TYPE_HOLDER");
    }

    @Override
    public @Nullable ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(
        chameleon,
        builder -> new JavaDocParser(builder, JavaParserUtil.getLanguageLevel(builder)).parseJavadocType(JavaParser.INSTANCE),
        false,
        LanguageLevel.JDK_1_3,
        level -> new JavaTypeEscapeLexer(new JavaLexer(level))
      );
    }
  }

  abstract class DocCommentElementType extends IReparseableElementType {
    private final JavaParserUtil.ParserWrapper myParser;

    public DocCommentElementType(String debugName) {
      super(debugName, JavaLanguage.INSTANCE);
      myParser = builder -> new JavaDocParser(builder, JavaParserUtil.getLanguageLevel(builder)).parseDocCommentText();
    }

    @Override
    public @Nullable ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragmentWithHighestLanguageLevel(chameleon, myParser, JavaDocLexer::new);
    }

    @Override
    public boolean isParsable(@Nullable ASTNode parent,
                              CharSequence buffer,
                              Language fileLanguage,
                              Project project) {
      IElementType targetType;
      if (StringUtil.startsWith(buffer, "/**") && StringUtil.endsWith(buffer, "*/")) {
        targetType = DOC_COMMENT;
      }
      else if (StringUtil.startsWith(buffer, "///")) {
        targetType = DOC_MARKDOWN_COMMENT;
      }
      else {
        return false;
      }

      PsiFile psiFile = parent.getPsi().getContainingFile();
      final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(psiFile);
      LanguageLevel languageLevel =
        moduleForPsiElement == null ? LanguageLevel.HIGHEST : EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(moduleForPsiElement);

      Lexer lexer = new JavaLexer(languageLevel);
      lexer.start(buffer);
      if (lexer.getTokenType() == targetType) {
        lexer.advance();
        return lexer.getTokenType() == null;
      }
      return false;
    }
  }
}
