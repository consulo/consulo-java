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

import javax.annotation.Nonnull;

import com.intellij.lang.ASTNode;
import consulo.logging.Logger;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiBlockStatement;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;

public class PsiBlockStatementImpl extends CompositePsiElement implements PsiBlockStatement {
  private static final Logger LOG = Logger.getInstance(PsiBlockStatementImpl.class);

  public PsiBlockStatementImpl() {
    super(Constants.BLOCK_STATEMENT);
  }

  @Override
  @Nonnull
  public PsiCodeBlock getCodeBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.BLOCK);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.BLOCK:
        return findChildByType(Constants.CODE_BLOCK);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == Constants.CODE_BLOCK) {
      return ChildRole.BLOCK;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitBlockStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiBlockStatement";
  }
}
