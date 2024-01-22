// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.light.LightExpressionList;
import com.intellij.java.language.impl.psi.scope.ElementClassFilter;
import com.intellij.java.language.impl.psi.scope.PatternResolveState;
import com.intellij.java.language.impl.psi.scope.processor.FilterScopeProcessor;
import com.intellij.java.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class PsiSwitchLabelStatementBaseImpl extends CompositePsiElement implements PsiSwitchLabelStatementBase {
  protected PsiSwitchLabelStatementBaseImpl(IElementType type) {
    super(type);
  }

  @Override
  public boolean isDefaultCase() {
    return findChildByType(JavaTokenType.DEFAULT_KEYWORD) != null;
  }

  @Override
  public PsiExpressionList getCaseValues() {
    PsiCaseLabelElementList elementList = getCaseLabelElementList();
    if (elementList == null) return null;
    PsiExpression[] expressions = PsiTreeUtil.getChildrenOfType(elementList, PsiExpression.class);
    expressions = expressions != null ? expressions : PsiExpression.EMPTY_ARRAY;
    return new LightExpressionList(getManager(), getLanguage(), expressions, elementList, elementList.getTextRange());
  }

  @Override
  @Nullable
  public PsiExpression getGuardExpression() {
    return PsiTreeUtil.getChildOfType(this, PsiExpression.class);
  }

  @Nullable
  @Override
  public PsiSwitchBlock getEnclosingSwitchBlock() {
    PsiElement codeBlock = getParent();
    if (codeBlock != null) {
      PsiElement switchBlock = codeBlock.getParent();
      if (switchBlock instanceof PsiSwitchBlock) {
        return (PsiSwitchBlock)switchBlock;
      }
    }
    return null;
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    if (lastParent instanceof PsiCaseLabelElementList) {
      PsiSwitchBlock switchStatement = getEnclosingSwitchBlock();
      if (switchStatement != null) {
        PsiExpression expression = switchStatement.getExpression();
        if (expression != null) {
          PsiType type = expression.getType();
          if (type instanceof PsiClassType) {
            PsiClass aClass = ((PsiClassType)type).resolve();
            if (aClass != null) {
              if (!aClass.processDeclarations(new FilterScopeProcessor(ElementClassFilter.ENUM_CONST, processor), state, this, place)) {
                return false;
              }
            }
          }
        }
      }
    }

    return true;
  }

  @Override
  public
  @Nullable
  PsiCaseLabelElementList getCaseLabelElementList() {
    return PsiTreeUtil.getChildOfType(this, PsiCaseLabelElementList.class);
  }

  protected boolean processPatternVariables(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, @Nonnull PsiElement place) {
    final PsiCaseLabelElementList patternsInCaseLabel = getCaseLabelElementList();
    if (patternsInCaseLabel == null) return true;
    if (!patternsInCaseLabel.processDeclarations(processor, state, null, place)) return false;

    PsiExpression guardExpression = getGuardExpression();
    if (guardExpression != null) {
      return guardExpression.processDeclarations(processor, PatternResolveState.WHEN_TRUE.putInto(state), null, place);
    }
    return true;
  }
}