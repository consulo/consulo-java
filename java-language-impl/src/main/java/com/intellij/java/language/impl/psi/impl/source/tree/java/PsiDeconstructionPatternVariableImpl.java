// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.java.language.psi.*;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.DECONSTRUCTION_PATTERN_VARIABLE;

public class PsiDeconstructionPatternVariableImpl extends CompositePsiElement implements PsiPatternVariable {
  public PsiDeconstructionPatternVariableImpl() {
    super(DECONSTRUCTION_PATTERN_VARIABLE);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPatternVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiDeconstructionPatternVariable";
  }

  @Override
  public
  @Nullable
  PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return false;
  }

  @Override
  public @Nonnull
  PsiElement getDeclarationScope() {
    return JavaSharedImplUtil.getPatternVariableDeclarationScope(this);
  }

  @Override
  public boolean isVarArgs() {
    return false;
  }

  @Override
  public @Nonnull
  PsiType getType() {
    return JavaSharedImplUtil.getType(getTypeElement(), getNameIdentifier());
  }

  @Override
  public @Nonnull
  PsiTypeElement getTypeElement() {
    return getPattern().getTypeElement();
  }

  @Override
  public
  @Nullable
  PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {

  }

  @Override
  public
  @Nullable
  Object computeConstantValue() {
    return null;
  }

  @Override
  public @Nonnull
  PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)Objects.requireNonNull(findPsiChildByType(JavaTokenType.IDENTIFIER));
  }

  @Override
  public @Nonnull
  String getName() {
    return getNameIdentifier().getText();
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  public @Nonnull
  PsiDeconstructionPattern getPattern() {
    return (PsiDeconstructionPattern)getParent();
  }
}
