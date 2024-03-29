/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.PatternResolveState;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Function;

public class PsiPolyadicExpressionImpl extends ExpressionPsiElement implements PsiPolyadicExpression {
  private static final Logger LOG = Logger.getInstance(PsiPolyadicExpressionImpl.class);

  public PsiPolyadicExpressionImpl() {
    super(JavaElementType.POLYADIC_EXPRESSION);
  }

  @Override
  @Nonnull
  public IElementType getOperationTokenType() {
    return ((PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN)).getTokenType();
  }

  @Override
  public PsiJavaToken getTokenBeforeOperand(@Nonnull PsiExpression operand) {
    PsiElement element = operand;
    while (element != null) {
      if (getChildRole(element.getNode()) == ChildRole.OPERATION_SIGN) {
        return (PsiJavaToken) element;
      }
      element = element.getPrevSibling();
    }
    return null;
  }

  @Override
  public PsiType getType() {
    return JavaResolveCache.getInstance(getProject()).getType(this, MY_TYPE_EVALUATOR);
  }

  private static final Function<PsiPolyadicExpressionImpl, PsiType> MY_TYPE_EVALUATOR = PsiPolyadicExpressionImpl::doGetType;

  @Nullable
  private static PsiType doGetType(PsiPolyadicExpressionImpl param) {
    PsiExpression[] operands = param.getOperands();
    PsiType lType = null;

    IElementType sign = param.getOperationTokenType();
    for (int i = 1; i < operands.length; i++) {
      PsiType rType = operands[i].getType();
      // optimization: if we can calculate type based on right type only
      PsiType type = TypeConversionUtil.calcTypeForBinaryExpression(null, rType, sign, false);
      if (type != TypeConversionUtil.NULL_TYPE) {
        return type;
      }
      if (lType == null) {
        lType = operands[0].getType();
      }
      lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, sign, true);
    }
    return lType;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.OPERATION_SIGN:
        return findChildByType(OUR_OPERATIONS_BIT_SET);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (OUR_OPERATIONS_BIT_SET.contains(child.getElementType())) {
      return ChildRole.OPERATION_SIGN;
    }
    return ChildRoleBase.NONE;
  }

  private static final TokenSet OUR_OPERATIONS_BIT_SET =
      TokenSet.create(JavaTokenType.OROR, JavaTokenType.ANDAND, JavaTokenType.OR, JavaTokenType.XOR, JavaTokenType.AND, JavaTokenType.EQEQ,
          JavaTokenType.NE, JavaTokenType.LT, JavaTokenType.GT, JavaTokenType.LE, JavaTokenType.GE, JavaTokenType.LTLT,
          JavaTokenType.GTGT, JavaTokenType.GTGTGT, JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.ASTERISK, JavaTokenType.DIV,
          JavaTokenType.PERC);


  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitPolyadicExpression(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Nonnull
  @Override
  public PsiExpression[] getOperands() {
    PsiExpression[] operands = cachedOperands;
    if (operands == null) {
      cachedOperands = operands = getChildrenAsPsiElements(ElementType.EXPRESSION_BIT_SET, PsiExpression.ARRAY_FACTORY);
    }
    return operands;
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    return processDeclarations(this, processor, state, lastParent, place);
  }

  static boolean processDeclarations(@Nonnull PsiPolyadicExpression expression,
                                     @Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    IElementType tokenType = expression.getOperationTokenType();
    boolean and = tokenType.equals(JavaTokenType.ANDAND);
    boolean or = tokenType.equals(JavaTokenType.OROR);
    if (!and && !or) {
      return true;
    }
    ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) {
      return true;
    }
    PatternResolveState wantedHint = PatternResolveState.fromBoolean(and);
    if (state.get(PatternResolveState.KEY) == wantedHint.invert()) {
      return true;
    }
    return PsiScopesUtil.walkChildrenScopes(expression, processor, wantedHint.putInto(state), lastParent, place);
  }

  private volatile PsiExpression[] cachedOperands;

  @Override
  public void clearCaches() {
    cachedOperands = null;
    super.clearCaches();
  }

  public String toString() {
    return "PsiPolyadicExpression: " + getText();
  }
}
