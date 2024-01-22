// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiType;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import jakarta.annotation.Nonnull;

/**
 * This class exists for compatibility only.
 * Will be removed together with {@link PsiSwitchLabelStatementBase#getCaseValues()} in the future.
 */
public class LightExpressionList extends LightElement implements PsiExpressionList {
  private final PsiExpression[] myExpressions;
  @Nonnull
  private final PsiElement myContext;
  private final TextRange myRange;

  public LightExpressionList(@Nonnull PsiManager manager,
                             @Nonnull Language language,
                             PsiExpression[] expressions,
                             @Nonnull PsiElement context,
                             TextRange range) {
    super(manager, language);
    myExpressions = expressions;
    myRange = range;
    myContext = context;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExpressionList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  @Nonnull
  public PsiExpression[] getExpressions() {
    return myExpressions;
  }

  @Override
  @Nonnull
  public PsiType[] getExpressionTypes() {
    PsiExpression[] expressions = getExpressions();
    PsiType[] types = PsiType.createArray(expressions.length);

    for (int i = 0; i < types.length; i++) {
      types[i] = expressions[i].getType();
    }

    return types;
  }

  @Override
  public TextRange getTextRange() {
    return myRange;
  }

  @Override
  public
  @Nonnull
  PsiElement getContext() {
    return myContext;
  }

  @Override
  public String toString() {
    return "PsiExpressionList";
  }
}
