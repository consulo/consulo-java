// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.scope;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;

public enum PatternResolveState {
  WHEN_TRUE,
  WHEN_FALSE,
  WHEN_BOTH,
  WHEN_NONE;

  public static final Key<PatternResolveState> KEY = Key.create("JavaPatternDeclarationHint");

  public static PatternResolveState fromBoolean(boolean value) {
    return value ? WHEN_TRUE : WHEN_FALSE;
  }

  public PatternResolveState invert() {
    switch (this) {
      case WHEN_TRUE:
        return WHEN_FALSE;
      case WHEN_FALSE:
        return WHEN_TRUE;
      default:
        return this;
    }
  }

  public ResolveState putInto(ResolveState rs) {
    return rs.put(KEY, this);
  }

  @Nonnull
  public static PatternResolveState stateAtParent(PsiPatternVariable element, PsiExpression parent) {
    PsiPattern pattern = element.getPattern();
    PatternResolveState state = WHEN_TRUE;
    for (PsiElement prev = pattern, current = prev.getParent(); prev != parent; prev = current, current = current.getParent()) {
      if (current instanceof PsiInstanceOfExpression || current instanceof PsiParenthesizedExpression ||
          current instanceof PsiPolyadicExpression &&
              (((PsiPolyadicExpression) current).getOperationTokenType() == JavaTokenType.ANDAND ||
                  ((PsiPolyadicExpression) current).getOperationTokenType() == JavaTokenType.OROR)) {
        continue;
      }
      if (current instanceof PsiPrefixExpression &&
          ((PsiPrefixExpression) current).getOperationTokenType() == JavaTokenType.EXCL) {
        state = state.invert();
        continue;
      }
      return WHEN_NONE;
    }
    return state;
  }
}
