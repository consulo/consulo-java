// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiDeconstructionList;
import com.intellij.java.language.psi.PsiPattern;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.*;

public class PsiDeconstructionListImpl extends CompositePsiElement implements PsiDeconstructionList {
  private final TokenSet PRIMARY_PATTERN_SET =
    TokenSet.create(TYPE_TEST_PATTERN, DECONSTRUCTION_PATTERN, PARENTHESIZED_PATTERN, UNNAMED_PATTERN);

  public PsiDeconstructionListImpl() {
    super(DECONSTRUCTION_LIST);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDeconstructionList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    if (PRIMARY_PATTERN_SET.contains(child.getElementType())) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @jakarta.annotation.Nullable Boolean before) {
    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByType(JavaTokenType.RPARENTH);
        before = Boolean.TRUE;
      }
      else {
        anchor = findChildByType(JavaTokenType.LPARENTH);
        before = Boolean.FALSE;
      }
    }

    TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && PRIMARY_PATTERN_SET.contains(first.getElementType())) {
      JavaSourceUtil.addSeparatingComma(this, first, PRIMARY_PATTERN_SET);
    }

    return firstAdded;
  }

  @Override
  @Nonnull
  public PsiPattern[] getDeconstructionComponents() {
    PsiPattern[] children = PsiTreeUtil.getChildrenOfType(this, PsiPattern.class);
    if (children == null) {
      return PsiPattern.EMPTY;
    }
    return children;
  }

  @Override
  public String toString() {
    return "PsiDeconstructionList";
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    PsiPattern[] components = getDeconstructionComponents();
    for (PsiPattern component : components) {
      component.processDeclarations(processor, state, null, place);
    }
    return true;
  }
}
