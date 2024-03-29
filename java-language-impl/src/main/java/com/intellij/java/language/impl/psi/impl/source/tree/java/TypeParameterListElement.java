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
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.impl.ast.TreeElement;
import consulo.language.util.CharTable;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

/**
 * @author max
 */
public class TypeParameterListElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(TypeParameterListElement.class);

  public TypeParameterListElement() {
    super(JavaElementType.TYPE_PARAMETER_LIST);
  }

  @Override
  public int getChildRole(final ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    final IElementType elType = child.getElementType();
    if (elType == JavaElementType.TYPE_PARAMETER) {
      return ChildRole.TYPE_PARAMETER_IN_LIST;
    } else if (elType == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    } else if (elType == JavaTokenType.LT) {
      return ChildRole.LT_IN_TYPE_LIST;
    } else if (elType == JavaTokenType.GT) {
      return ChildRole.GT_IN_TYPE_LIST;
    } else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public TreeElement addInternal(final TreeElement first, final ASTNode last, ASTNode anchor, Boolean before) {
    TreeElement lt = (TreeElement) findChildByRole(ChildRole.LT_IN_TYPE_LIST);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (lt == null) {
      lt = Factory.createSingleLeafElement(JavaTokenType.LT, "<", 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, getFirstChildNode(), Boolean.TRUE);
    }

    TreeElement gt = (TreeElement) findChildByRole(ChildRole.GT_IN_TYPE_LIST);
    if (gt == null) {
      gt = Factory.createSingleLeafElement(JavaTokenType.GT, ">", 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, getLastChildNode(), Boolean.FALSE);
    }

    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = gt;
        before = Boolean.TRUE;
      } else {
        anchor = lt;
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && first.getElementType() == JavaElementType.TYPE_PARAMETER) {
      for (ASTNode child = first.getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (child.getElementType() == JavaElementType.TYPE_PARAMETER) {
          final TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, first, Boolean.FALSE);
          break;
        }
      }
      for (ASTNode child = first.getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (child.getElementType() == JavaElementType.TYPE_PARAMETER) {
          final TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }
    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@Nonnull final ASTNode child) {
    if (child.getElementType() == JavaElementType.TYPE_PARAMETER) {
      final ASTNode next = PsiImplUtil.skipWhitespaceAndComments(child.getTreeNext());
      if (next != null && next.getElementType() == JavaTokenType.COMMA) {
        deleteChildInternal(next);
      } else {
        final ASTNode prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
        if (prev != null && prev.getElementType() == JavaTokenType.COMMA) {
          deleteChildInternal(prev);
        }
      }
    }
    super.deleteChildInternal(child);
    if (child.getElementType() == JavaElementType.TYPE_PARAMETER) {
      final ASTNode lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      final ASTNode next = PsiImplUtil.skipWhitespaceAndComments(lt.getTreeNext());
      if (next != null && next.getElementType() == JavaTokenType.GT) {
        deleteChildInternal(lt);
        deleteChildInternal(next);
      }
    }
  }
}
