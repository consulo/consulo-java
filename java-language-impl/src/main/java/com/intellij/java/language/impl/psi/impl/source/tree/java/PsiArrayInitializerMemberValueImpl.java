/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiArrayInitializerMemberValue;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author ven
 */
public class PsiArrayInitializerMemberValueImpl extends CompositePsiElement implements PsiArrayInitializerMemberValue {
  private static final Logger LOG = Logger.getInstance(PsiArrayInitializerMemberValueImpl.class);
  private static final TokenSet MEMBER_SET = ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET;

  public PsiArrayInitializerMemberValueImpl() {
    super(JavaElementType.ANNOTATION_ARRAY_INITIALIZER);
  }

  @Override
  @Nonnull
  public PsiAnnotationMemberValue[] getInitializers() {
    return getChildrenAsPsiElements(MEMBER_SET, PsiAnnotationMemberValue.ARRAY_FACTORY);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch (role) {
      default:
        return null;

      case ChildRole.LBRACE:
        return findChildByType(JavaTokenType.LBRACE);

      case ChildRole.RBRACE:
        return findChildByType(JavaTokenType.RBRACE);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    IElementType i = child.getElementType();
    if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    } else if (i == JavaTokenType.LBRACE) {
      return ChildRole.LBRACE;
    } else if (i == JavaTokenType.RBRACE) {
      return ChildRole.RBRACE;
    } else if (MEMBER_SET.contains(child.getElementType())) {
      return ChildRole.ANNOTATION_VALUE;
    }
    return ChildRoleBase.NONE;
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (MEMBER_SET.contains(first.getElementType()) && MEMBER_SET.contains(last.getElementType())) {
      TreeElement firstAdded = super.addInternal(first, last, anchor, before);
      JavaSourceUtil.addSeparatingComma(this, first, MEMBER_SET);
      return firstAdded;
    }

    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    if (MEMBER_SET.contains(child.getElementType())) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }

  @Override
  public final void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitAnnotationArrayInitializer(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiArrayInitializerMemberValue:" + getText();
  }
}