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

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiClassObjectAccessExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElementVisitor;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

public class PsiClassObjectAccessExpressionImpl extends ExpressionPsiElement implements PsiClassObjectAccessExpression, Constants {
  private static final Logger LOG = Logger.getInstance(PsiClassObjectAccessExpressionImpl.class);

  public PsiClassObjectAccessExpressionImpl() {
    super(CLASS_OBJECT_ACCESS_EXPRESSION);
  }

  @Override
  public PsiType getType() {
    return PsiImplUtil.getType(this);
  }

  @Override
  @Nonnull
  public PsiTypeElement getOperand() {
    return (PsiTypeElement) findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.TYPE:
        return findChildByType(TYPE);

      case ChildRole.DOT:
        return findChildByType(DOT);

      case ChildRole.CLASS_KEYWORD:
        return findChildByType(CLASS_KEYWORD);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == TYPE) {
      return ChildRole.TYPE;
    } else if (i == DOT) {
      return ChildRole.DOT;
    } else if (i == CLASS_KEYWORD) {
      return ChildRole.CLASS_KEYWORD;
    } else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@jakarta.annotation.Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitClassObjectAccessExpression(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiClassObjectAccessExpression:" + getText();
  }
}

