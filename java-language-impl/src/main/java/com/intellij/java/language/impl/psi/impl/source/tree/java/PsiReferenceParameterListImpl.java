/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import consulo.language.ast.*;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.util.CharTable;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

/**
 *  @author dsl
 */
public class PsiReferenceParameterListImpl extends CompositePsiElement implements PsiReferenceParameterList {
  private static final Logger LOG = Logger.getInstance(PsiReferenceParameterListImpl.class);
  private static final TokenSet TYPE_SET = TokenSet.create(JavaElementType.TYPE);

  public PsiReferenceParameterListImpl() {
    super(JavaElementType.REFERENCE_PARAMETER_LIST);
  }

  @Override
  @Nonnull
  public PsiTypeElement[] getTypeParameterElements() {
    return getChildrenAsPsiElements(JavaElementType.TYPE, PsiTypeElement.ARRAY_FACTORY);
  }

  @Override
  public int getTypeArgumentCount() {
    int children = countChildren(TYPE_SET);
    if (children == 1) {
      PsiTypeElement typeElement = (PsiTypeElement)findChildByType(JavaElementType.TYPE);
      LOG.assertTrue(typeElement != null);
      PsiType soleType = typeElement.getType();
      if (soleType instanceof PsiDiamondType) {
        return ((PsiDiamondType)soleType).resolveInferredTypes().getInferredTypes().size();
      }
    }
    return children;
  }

  @Override
  @Nonnull
  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByReferenceParameterList(this);
  }

  @Override
  public int getChildRole(ASTNode child) {
    IElementType i = child.getElementType();
    if (i == JavaElementType.TYPE) {
      return ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST;
    }
    else if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaTokenType.LT) {
      return ChildRole.LT_IN_TYPE_LIST;
    }
    else if (i == JavaTokenType.GT) {
      return ChildRole.GT_IN_TYPE_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LT_IN_TYPE_LIST:
        if (getFirstChildNode() != null && getFirstChildNode().getElementType() == JavaTokenType.LT){
          return getFirstChildNode();
        }
        else{
          return null;
        }

      case ChildRole.GT_IN_TYPE_LIST:
        if (getLastChildNode() != null && getLastChildNode().getElementType() == JavaTokenType.GT){
          return getLastChildNode();
        }
        else{
          return null;
        }
    }
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before){
    if (first == last && first.getElementType() == JavaElementType.TYPE){
      if (getLastChildNode() != null && getLastChildNode().getElementType() == TokenType.ERROR_ELEMENT){
        super.deleteChildInternal(getLastChildNode());
      }
    }

    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    if (getFirstChildNode()== null || getFirstChildNode().getElementType() != JavaTokenType.LT){
      TreeElement lt = Factory.createSingleLeafElement(JavaTokenType.LT, "<", 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, getFirstChildNode(), Boolean.TRUE);
    }
    if (getLastChildNode() == null || getLastChildNode().getElementType() != JavaTokenType.GT){
      TreeElement gt = Factory.createSingleLeafElement(JavaTokenType.GT, ">", 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, getLastChildNode(), Boolean.FALSE);
    }

    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && first.getElementType() == JavaElementType.TYPE){
      for(ASTNode child = first.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (child.getElementType() == JavaElementType.TYPE){
          TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, first, Boolean.FALSE);
          break;
        }
      }
      for(ASTNode child = first.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (child.getElementType() == JavaElementType.TYPE){
          TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    if (child.getElementType() == JavaElementType.TYPE){
      ASTNode next = PsiImplUtil.skipWhitespaceAndComments(child.getTreeNext());
      if (next != null && next.getElementType() == JavaTokenType.COMMA){
        deleteChildInternal(next);
      }
      else{
        ASTNode prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
        if (prev != null && prev.getElementType() == JavaTokenType.COMMA){
          deleteChildInternal(prev);
        }
      }
    }

    super.deleteChildInternal(child);

    if (getTypeParameterElements().length == 0){
      ASTNode lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      if (lt != null){
        deleteChildInternal(lt);
      }

      ASTNode gt = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
      if (gt != null){
        deleteChildInternal(gt);
      }
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReferenceParameterList";
  }
}
