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

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.impl.ast.TreeElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.util.CharTable;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

public class PsiArrayInitializerExpressionImpl extends ExpressionPsiElement implements PsiArrayInitializerExpression, Constants {
  private static final Logger LOG = Logger.getInstance(PsiArrayInitializerExpressionImpl.class);

  public PsiArrayInitializerExpressionImpl() {
    super(ARRAY_INITIALIZER_EXPRESSION);
  }

  @Override
  @Nonnull
  public PsiExpression[] getInitializers(){
    return getChildrenAsPsiElements(EXPRESSION_BIT_SET, PsiExpression.ARRAY_FACTORY);
  }

  @Override
  public PsiType getType(){
    if (getTreeParent() instanceof PsiNewExpression){
      if (getTreeParent().getChildRole(this) == ChildRole.ARRAY_INITIALIZER){
        return ((PsiNewExpression)getTreeParent()).getType();
      }
    }
    else if (getTreeParent() instanceof PsiVariable){
      return ((PsiVariable)getTreeParent()).getType();
    }
    else if (getTreeParent() instanceof PsiArrayInitializerExpression){
      PsiType parentType = ((PsiArrayInitializerExpression)getTreeParent()).getType();
      if (!(parentType instanceof PsiArrayType)) return null;
      final PsiType componentType = ((PsiArrayType)parentType).getComponentType();
      return componentType instanceof PsiArrayType ? componentType : null;
    }
    else if (getTreeParent() instanceof FieldElement){
      return ((PsiField)getParent()).getType();
    }

    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LBRACE:
        return findChildByType(LBRACE);

      case ChildRole.RBRACE:
        return findChildByType(RBRACE);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LBRACE) {
      return ChildRole.LBRACE;
    }
    else if (i == RBRACE) {
      return ChildRole.RBRACE;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION_IN_LIST;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitArrayInitializerExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiArrayInitializerExpression:" + getText();
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.RBRACE);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LBRACE);
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (ElementType.EXPRESSION_BIT_SET.contains(first.getElementType())) {
     final CharTable charTab = SharedImplUtil.findCharTableByTree(this);
      for (ASTNode child = first.getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, charTab, getManager());
          super.addInternal(comma, comma, first, Boolean.FALSE);
          break;
        }
      }
      for (ASTNode child = first.getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, charTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    return firstAdded;
  }
}
