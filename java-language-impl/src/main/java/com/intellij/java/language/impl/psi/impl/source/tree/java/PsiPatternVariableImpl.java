// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.java.language.psi.*;
import consulo.content.scope.SearchScope;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Objects;

public class PsiPatternVariableImpl extends CompositePsiElement implements PsiPatternVariable, Constants {
  public PsiPatternVariableImpl() {
    super(PATTERN_VARIABLE);
  }

  @Override
  public PsiIdentifier setName(@Nonnull String name) throws IncorrectOperationException {
    PsiIdentifier identifier = getNameIdentifier();
    return (PsiIdentifier) PsiImplUtil.setName(identifier, name);
  }

  @Override
  @Nonnull
  public PsiIdentifier getNameIdentifier() {
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiIdentifier.class));
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitPatternVariable(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Nonnull
  @Override
  public PsiPattern getPattern() {
    return (PsiPattern) getParent();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Nullable
  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Nonnull
  @Override
  public PsiType getType() {
    return JavaSharedImplUtil.getType(getTypeElement(), getNameIdentifier());
  }

  @Nonnull
  @Override
  public PsiElement getDeclarationScope() {
    PsiElement parent = getPattern().getParent();
    if (!(parent instanceof PsiInstanceOfExpression)) {
      return parent;
    }
    boolean negated = false;
    for (PsiElement nextParent = parent.getParent(); ; parent = nextParent, nextParent = parent.getParent()) {
      if (nextParent instanceof PsiParenthesizedExpression) {
        continue;
      }
      if (nextParent instanceof PsiConditionalExpression && parent == ((PsiConditionalExpression) nextParent).getCondition()) {
        return nextParent;
      }
      if (nextParent instanceof PsiPrefixExpression && ((PsiPrefixExpression) nextParent).getOperationTokenType().equals(EXCL)) {
        negated = !negated;
        continue;
      }
      if (nextParent instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression) nextParent).getOperationTokenType();
        if (tokenType.equals(ANDAND) && !negated || tokenType.equals(OROR) && negated) {
          continue;
        }
      }
      if (nextParent instanceof PsiIfStatement) {
        while (nextParent.getParent() instanceof PsiLabeledStatement) {
          nextParent = nextParent.getParent();
        }
        return nextParent.getParent();
      }
      if (nextParent instanceof PsiConditionalLoopStatement) {
        if (!negated) {
          return nextParent;
        }
        while (nextParent.getParent() instanceof PsiLabeledStatement) {
          nextParent = nextParent.getParent();
        }
        return nextParent.getParent();
      }
      return parent;
    }
  }

  @Override
  public boolean isVarArgs() {
    return false;
  }

  @Nonnull
  @Override
  public PsiTypeElement getTypeElement() {
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiTypeElement.class));
  }

  @Nullable
  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Nonnull
  @Override
  public String getName() {
    PsiIdentifier identifier = getNameIdentifier();
    return identifier.getText();
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  @Nullable
  @Override
  public PsiModifierList getModifierList() {
    return (PsiModifierList) findPsiChildByType(JavaElementType.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    final PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Nonnull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public void delete() throws IncorrectOperationException {
    PsiPattern pattern = getPattern();
    if (pattern instanceof PsiTypeTestPattern) {
      replace(getTypeElement());
      return;
    }
    super.delete();
  }

  @Override
  public String toString() {
    return "PsiPatternVariable:" + getName();
  }
}

