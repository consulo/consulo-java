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

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaElementVisitor;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiReturnStatement;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.ast.TreeUtil;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import consulo.language.ast.IElementType;
import consulo.language.ast.ChildRoleBase;
import consulo.logging.Logger;

public class PsiReturnStatementImpl extends CompositePsiElement implements PsiReturnStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiReturnStatementImpl.class);

  public PsiReturnStatementImpl() {
    super(RETURN_STATEMENT);
  }

  @Override
  public PsiExpression getReturnValue() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.RETURN_VALUE);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.RETURN_KEYWORD:
        return findChildByType(RETURN_KEYWORD);

      case ChildRole.RETURN_VALUE:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == RETURN_KEYWORD) {
      return ChildRole.RETURN_KEYWORD;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.RETURN_VALUE;
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReturnStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReturnStatement";
  }
}
