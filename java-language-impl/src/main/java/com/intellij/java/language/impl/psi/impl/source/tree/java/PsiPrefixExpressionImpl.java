/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.PatternResolveState;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

public class PsiPrefixExpressionImpl extends ExpressionPsiElement implements PsiPrefixExpression {
  private static final Logger LOG = Logger.getInstance(PsiPrefixExpressionImpl.class);

  public PsiPrefixExpressionImpl() {
    super(JavaElementType.PREFIX_EXPRESSION);
  }

  @Override
  public PsiExpression getOperand() {
    return (PsiExpression) findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  @Override
  @Nonnull
  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  @Override
  @Nonnull
  public IElementType getOperationTokenType() {
    return getOperationSign().getTokenType();
  }

  @Override
  public PsiType getType() {
    PsiExpression operand = getOperand();
    if (operand == null) return null;
    PsiType type = operand.getType();
    IElementType opCode = getOperationTokenType();
    if (opCode == JavaTokenType.PLUS || opCode == JavaTokenType.MINUS || opCode == JavaTokenType.TILDE) {
      if (type == null) return null;
      if (type instanceof PsiClassType) type = PsiPrimitiveType.getUnboxedType(type);
      return PsiType.BYTE.equals(type) || PsiType.CHAR.equals(type) || PsiType.SHORT.equals(type) ? PsiType.INT : type;
    } else if (opCode == JavaTokenType.PLUSPLUS || opCode == JavaTokenType.MINUSMINUS) {
      return type;
    } else if (opCode == JavaTokenType.EXCL) {
      return PsiType.BOOLEAN;
    } else {
      LOG.assertTrue(false);
      return null;
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.OPERATION_SIGN:
        return getFirstChildNode();

      case ChildRole.OPERAND:
        return ElementType.EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child == getFirstChildNode()) return ChildRole.OPERATION_SIGN;
    if (child == getLastChildNode() && ElementType.EXPRESSION_BIT_SET.contains(child.getElementType()))
      return ChildRole.OPERAND;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitPrefixExpression(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    if (lastParent != null || !getOperationTokenType().equals(JavaTokenType.EXCL)) return true;
    ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) return true;
    PatternResolveState hint = state.get(PatternResolveState.KEY);
    if (hint == null) return true;
    return PsiScopesUtil.walkChildrenScopes(this, processor, hint.invert().putInto(state), null, place);
  }

  public String toString() {
    return "PsiPrefixExpression:" + getText();
  }
}

