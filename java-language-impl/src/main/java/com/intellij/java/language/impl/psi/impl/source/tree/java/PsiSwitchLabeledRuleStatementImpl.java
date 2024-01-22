// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class PsiSwitchLabeledRuleStatementImpl extends PsiSwitchLabelStatementBaseImpl implements PsiSwitchLabeledRuleStatement {
  private static final TokenSet BODY_STATEMENTS =
    TokenSet.create(JavaElementType.BLOCK_STATEMENT, JavaElementType.THROW_STATEMENT, JavaElementType.EXPRESSION_STATEMENT);

  public PsiSwitchLabeledRuleStatementImpl() {
    super(JavaElementType.SWITCH_LABELED_RULE);
  }

  @Override
  public PsiStatement getBody() {
    return (PsiStatement)findPsiChildByType(BODY_STATEMENTS);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchLabeledRuleStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSwitchLabeledRule";
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    if (!super.processDeclarations(processor, state, lastParent, place)) return false;

    // Do not resolve variables that don't come from the body of this {@link PsiSwitchLabeledRuleStatement}
    if (lastParent == null) return true;
    // Do not resolve references that come from the list of elements in this case rule
    if (lastParent instanceof PsiCaseLabelElementList) return true;

    if (!shouldProcess()) return true;

    return processPatternVariables(processor, state, place);
  }

  private boolean shouldProcess() {
    final PsiCaseLabelElementList elementList = getCaseLabelElementList();
    if (elementList == null) return false;

    final PsiCaseLabelElement[] elements = elementList.getElements();
    if (elements.length == 1) return true;
    else if (elements.length > 2 || elements.length == 0) return false;

    final PsiElement first = stripParensIfNecessary(elements[0]);
    final PsiElement second = stripParensIfNecessary(elements[1]);

    if (first == null || second == null) return true;

    return firstPatternVariableSecondNull(first, second) || firstPatternVariableSecondNull(second, first);
  }

  private static boolean firstPatternVariableSecondNull(PsiElement first, @Nonnull PsiElement second) {
    return first instanceof PsiTypeTestPattern &&
      second.getNode().getFirstChildNode().getElementType() == JavaTokenType.NULL_KEYWORD;
  }

  private static
  @Nullable
  PsiElement stripParensIfNecessary(@Nonnull PsiCaseLabelElement element) {
    return element instanceof PsiExpression
      ? PsiUtil.skipParenthesizedExprDown((PsiExpression)element)
      : element;
  }
}