// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiLiteralStub;
import com.intellij.java.language.impl.psi.impl.source.JavaStubPsiElement;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.injected.StringLiteralEscaper;
import com.intellij.java.language.impl.util.text.LiteralFormatUtil;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiLiteralUtil;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.LeafElement;
import consulo.language.impl.psi.ResolveScopeManager;
import consulo.language.psi.*;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class PsiLiteralExpressionImpl
    extends JavaStubPsiElement<PsiLiteralStub>
    implements PsiLiteralExpression, PsiLanguageInjectionHost, ContributedReferenceHost {

  private static final TokenSet NUMERIC_LITERALS = TokenSet.orSet(ElementType.INTEGER_LITERALS, ElementType.REAL_LITERALS);

  public PsiLiteralExpressionImpl(@Nonnull PsiLiteralStub stub) {
    super(stub, JavaStubElementTypes.LITERAL_EXPRESSION);
  }

  public PsiLiteralExpressionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  @Nonnull
  public PsiElement[] getChildren() {
    return ((CompositeElement) getNode()).getChildrenAsPsiElements((TokenSet) null, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiType getType() {
    final IElementType type = getLiteralElementType();
    if (type == JavaTokenType.INTEGER_LITERAL) {
      return PsiType.INT;
    }
    if (type == JavaTokenType.LONG_LITERAL) {
      return PsiType.LONG;
    }
    if (type == JavaTokenType.FLOAT_LITERAL) {
      return PsiType.FLOAT;
    }
    if (type == JavaTokenType.DOUBLE_LITERAL) {
      return PsiType.DOUBLE;
    }
    if (type == JavaTokenType.CHARACTER_LITERAL) {
      return PsiType.CHAR;
    }
    if (ElementType.STRING_LITERALS.contains(type)) {
      PsiFile file = getContainingFile();
      return PsiType.getJavaLangString(file.getManager(), ResolveScopeManager.getElementResolveScope(file));
    }
    if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD) {
      return PsiType.BOOLEAN;
    }
    if (type == JavaTokenType.NULL_KEYWORD) {
      return PsiType.NULL;
    }
    return null;
  }

  @Override
  public boolean isTextBlock() {
    return getLiteralElementType() == JavaTokenType.TEXT_BLOCK_LITERAL;
  }

  public IElementType getLiteralElementType() {
    PsiLiteralStub stub = getGreenStub();
    if (stub != null) {
      return stub.getLiteralType();
    }

    return getNode().getFirstChildNode().getElementType();
  }

  public String getCanonicalText() {
    IElementType type = getLiteralElementType();
    return NUMERIC_LITERALS.contains(type) ? LiteralFormatUtil.removeUnderscores(getText()) : getText();
  }

  @Override
  public String getText() {
    PsiLiteralStub stub = getGreenStub();
    if (stub != null) {
      return stub.getLiteralText();
    }

    return super.getText();
  }

  @Override
  public Object getValue() {
    final IElementType type = getLiteralElementType();
    if (type == JavaTokenType.TRUE_KEYWORD) {
      return Boolean.TRUE;
    }
    if (type == JavaTokenType.FALSE_KEYWORD) {
      return Boolean.FALSE;
    }

    if (type == JavaTokenType.STRING_LITERAL) {
      return internedParseStringCharacters(PsiLiteralUtil.getStringLiteralContent(this));
    }
    if (type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      return internedParseStringCharacters(PsiLiteralUtil.getTextBlockText(this));
    }

    String text = NUMERIC_LITERALS.contains(type) ? StringUtil.toLowerCase(getCanonicalText()) : getCanonicalText();
    final int textLength = text.length();

    if (type == JavaTokenType.INTEGER_LITERAL) {
      return PsiLiteralUtil.parseInteger(text);
    }
    if (type == JavaTokenType.LONG_LITERAL) {
      return PsiLiteralUtil.parseLong(text);
    }
    if (type == JavaTokenType.FLOAT_LITERAL) {
      return PsiLiteralUtil.parseFloat(text);
    }
    if (type == JavaTokenType.DOUBLE_LITERAL) {
      return PsiLiteralUtil.parseDouble(text);
    }

    if (type == JavaTokenType.CHARACTER_LITERAL) {
      if (textLength == 1 || !StringUtil.endsWithChar(text, '\'')) {
        return null;
      }
      text = text.substring(1, textLength - 1);
      CharSequence chars = CodeInsightUtilCore.parseStringCharacters(text, null);
      if (chars == null) {
        return null;
      }
      if (chars.length() != 1) {
        return null;
      }
      return chars.charAt(0);
    }

    return null;
  }

  @Nullable
  private static String internedParseStringCharacters(final String chars) {
    if (chars == null) {
      return null;
    }
    final CharSequence outChars = CodeInsightUtilCore.parseStringCharacters(chars, null);
    return outChars == null ? null : outChars.toString();
  }

  public static boolean parseStringCharacters(@Nonnull String chars, @Nonnull StringBuilder outChars, @Nullable int[] sourceOffsets) {
    return CodeInsightUtilCore.parseStringCharacters(chars, outChars, sourceOffsets);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitLiteralExpression(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiLiteralExpression:" + getText();
  }

  @Override
  public boolean isValidHost() {
    return ElementType.TEXT_LITERALS.contains(getLiteralElementType());
  }

  @Override
  @Nonnull
  public PsiReference[] getReferences() {
    IElementType type = getLiteralElementType();
    return ElementType.STRING_LITERALS.contains(type) || type == JavaTokenType.INTEGER_LITERAL  // int literals could refer to SQL parameters
        ? PsiReferenceService.getService().getContributedReferences(this)
        : PsiReference.EMPTY_ARRAY;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@Nonnull final String text) {
    ASTNode valueNode = getNode().getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement) valueNode).replaceWithText(text);
    return this;
  }

  @Nonnull
  @Override
  public LiteralTextEscaper<PsiLiteralExpressionImpl> createLiteralTextEscaper() {
    return new StringLiteralEscaper<>(this);
  }
}