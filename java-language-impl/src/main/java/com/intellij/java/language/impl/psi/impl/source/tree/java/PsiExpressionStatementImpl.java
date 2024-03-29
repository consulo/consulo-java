/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionStatement;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.DebugUtil;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElementVisitor;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

public class PsiExpressionStatementImpl extends CompositePsiElement implements PsiExpressionStatement {
  private static final Logger LOG = Logger.getInstance(PsiExpressionStatementImpl.class);

  public PsiExpressionStatementImpl() {
    super(JavaElementType.EXPRESSION_STATEMENT);
  }

  @Override
  @Nonnull
  public PsiExpression getExpression() {
    PsiExpression expression = (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(findChildByType(ElementType.EXPRESSION_BIT_SET));
    if (expression != null) return expression;
    LOG.error("Illegal PSI: \n" + DebugUtil.psiToString(getParent(), false));
    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.EXPRESSION:
        return findChildByType(ElementType.EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExpressionStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiExpressionStatement";
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    if (getChildRole(child) == ChildRole.EXPRESSION) {
      getTreeParent().deleteChildInternal(this);
    }
    else {
      super.deleteChildInternal(child);
    }
  }
}
