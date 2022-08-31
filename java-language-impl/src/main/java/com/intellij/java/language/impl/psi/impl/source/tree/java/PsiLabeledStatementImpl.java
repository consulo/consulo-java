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

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiLabeledStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.util.IncorrectOperationException;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PsiLabeledStatementImpl extends CompositePsiElement implements PsiLabeledStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiLabeledStatementImpl.class);

  public PsiLabeledStatementImpl() {
    super(LABELED_STATEMENT);
  }

  @Override
  @Nonnull
  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.LABEL_NAME);
  }

  @Override
  public PsiStatement getStatement() {
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.STATEMENT);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.STATEMENT:
        return PsiImplUtil.findStatementChild(this);

      case ChildRole.COLON:
        return findChildByType(COLON);

      case ChildRole.LABEL_NAME:
        return getFirstChildNode();
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == IDENTIFIER) {
      return ChildRole.LABEL_NAME;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else {
      if (child.getPsi() instanceof PsiStatement) {
        return ChildRole.STATEMENT;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLabeledStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiLabeledStatement";
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    if (lastParent != null && lastParent.getParent() != this){
      PsiElement[] children = getChildren();
      for (PsiElement aChildren : children) {
        if (!aChildren.processDeclarations(processor, state, null, place)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public String getName() {
    return getLabelIdentifier().getText();
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getLabelIdentifier(), name);
    return this;
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    return new LocalSearchScope(this);
  }

  @Nullable
  @Override
  public PsiElement getNameIdentifier() {
    return getLabelIdentifier();
  }
}
