// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.DECONSTRUCTION_PATTERN;

public class PsiDeconstructionPatternImpl extends CompositePsiElement implements PsiDeconstructionPattern {
  public PsiDeconstructionPatternImpl() {
    super(DECONSTRUCTION_PATTERN);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDeconstructionPattern(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nonnull
  PsiDeconstructionList getDeconstructionList() {
    PsiDeconstructionList recordStructurePattern =
      (PsiDeconstructionList)findPsiChildByType(JavaElementType.DECONSTRUCTION_LIST);
    assert recordStructurePattern != null; // guaranteed by parser
    return recordStructurePattern;
  }

  @Override
  public @Nonnull
  PsiTypeElement getTypeElement() {
    PsiTypeElement type = (PsiTypeElement)findPsiChildByType(JavaElementType.TYPE);
    assert type != null; // guaranteed by parser
    return type;
  }

  @Override
  @Nullable
  public PsiPatternVariable getPatternVariable() {
    return (PsiPatternVariable)findPsiChildByType(JavaElementType.DECONSTRUCTION_PATTERN_VARIABLE);
  }

  @Override
  public String getName() {
    PsiElement identifier = getNameIdentifier();
    return identifier == null
      ? null
      : identifier.getText();
  }

  @Nullable
  private PsiElement getNameIdentifier() {
    return findPsiChildByType(JavaTokenType.IDENTIFIER);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);

    boolean shouldContinue = getDeconstructionList().processDeclarations(processor, state, lastParent, place);
    if (!shouldContinue) return false;

    PsiPatternVariable variable = getPatternVariable();
    if (variable != lastParent && variable != null) {
      return processor.execute(variable, state);
    }
    return true;
  }

  @Override
  public String toString() {
    String name = getName();
    if (name == null) {
      return "PsiDeconstructionPattern";
    }
    return "PsiDeconstructionPattern " + name;
  }
}
