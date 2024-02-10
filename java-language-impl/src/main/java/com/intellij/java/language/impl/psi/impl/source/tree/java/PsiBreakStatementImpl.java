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

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.PsiLabelReference;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

public class PsiBreakStatementImpl extends CompositePsiElement implements PsiBreakStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiBreakStatementImpl.class);

  public PsiBreakStatementImpl() {
    super(BREAK_STATEMENT);
  }

  @Override
  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier) findChildByRoleAsPsiElement(ChildRole.LABEL);
  }

  @Override
  public PsiStatement findExitedStatement() {
    PsiIdentifier label = getLabelIdentifier();
    if (label == null) {
      for (ASTNode parent = getTreeParent(); parent != null; parent = parent.getTreeParent()) {
        IElementType i = parent.getElementType();
        if (i == FOR_STATEMENT || i == WHILE_STATEMENT || i == DO_WHILE_STATEMENT || i == SWITCH_STATEMENT || i == FOREACH_STATEMENT) {
          return (PsiStatement) SourceTreeToPsiMap.treeElementToPsi(parent);
        } else if (i == METHOD || i == CLASS_INITIALIZER) {
          return null; // do not pass through anonymous/local class
        }
      }
    } else {
      String labelName = label.getText();
      for (CompositeElement parent = getTreeParent(); parent != null; parent = parent.getTreeParent()) {
        if (parent.getElementType() == LABELED_STATEMENT) {
          ASTNode statementLabel = parent.findChildByRole(ChildRole.LABEL_NAME);
          if (statementLabel.getText().equals(labelName)) {
            return ((PsiLabeledStatement) SourceTreeToPsiMap.treeElementToPsi(parent)).getStatement();
          }
        }

        if (parent.getElementType() == METHOD || parent.getElementType() == CLASS_INITIALIZER)
          return null; // do not pass through anonymous/local class
      }
    }
    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.BREAK_KEYWORD:
        return findChildByType(BREAK_KEYWORD);

      case ChildRole.LABEL:
        return findChildByType(IDENTIFIER);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == BREAK_KEYWORD) {
      return ChildRole.BREAK_KEYWORD;
    } else if (i == IDENTIFIER) {
      return ChildRole.LABEL;
    } else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    } else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitBreakStatement(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiReference getReference() {
    final PsiReference[] references = getReferences();
    if (references != null && references.length > 0)
      return references[0];
    return null;
  }

  @Override
  @Nonnull
  public PsiReference[] getReferences() {
    if (getLabelIdentifier() == null)
      return PsiReference.EMPTY_ARRAY;
    return new PsiReference[]{new PsiLabelReference(this, getLabelIdentifier())};
  }

  public String toString() {
    return "PsiBreakStatement";
  }
}
