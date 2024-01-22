// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiDeconstructionPattern;
import com.intellij.java.language.psi.PsiForeachPatternStatement;
import com.intellij.java.language.psi.PsiPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

import java.util.Objects;

public class PsiForeachPatternStatementImpl extends PsiForeachStatementBaseImpl implements PsiForeachPatternStatement, Constants {
  public PsiForeachPatternStatementImpl() {
    super(FOREACH_PATTERN_STATEMENT);
  }

  @Override
  public String toString() {
    return "PsiForeachPatternStatement";
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this || lastParent == getIteratedValue())
      // Parent element should not see our vars
      return true;

    PsiPattern pattern = getIterationPattern();
    if (pattern instanceof PsiDeconstructionPattern) {
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)pattern;
      return deconstructionPattern.processDeclarations(processor, state, lastParent, place);
    }
    return false;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitForeachPatternStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nonnull
  PsiPattern getIterationPattern() {
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiPattern.class));
  }
}
