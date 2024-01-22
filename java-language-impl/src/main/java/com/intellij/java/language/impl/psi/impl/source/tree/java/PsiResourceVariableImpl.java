/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

public class PsiResourceVariableImpl extends PsiLocalVariableImpl implements PsiResourceVariable {
  public PsiResourceVariableImpl() {
    super(JavaElementType.RESOURCE_VARIABLE);
  }

  @Nonnull
  @Override
  public PsiElement[] getDeclarationScope() {
    final PsiResourceList resourceList = (PsiResourceList) getParent();
    final PsiTryStatement tryStatement = (PsiTryStatement) resourceList.getParent();
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    return tryBlock != null ? new PsiElement[]{
        resourceList,
        tryBlock
    } : new PsiElement[]{resourceList};
  }

  @Nonnull
  @Override
  public PsiTypeElement getTypeElement() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiTypeElement.class);
  }

  @Override
  public PsiModifierList getModifierList() {
    return PsiTreeUtil.getChildOfType(this, PsiModifierList.class);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    final PsiElement next = PsiTreeUtil.skipSiblingsForward(this, PsiWhiteSpace.class, PsiComment.class);
    if (PsiUtil.isJavaToken(next, JavaTokenType.SEMICOLON)) {
      getParent().deleteChildRange(this, next);
      return;
    }

    final PsiElement prev = PsiTreeUtil.skipSiblingsBackward(this, PsiWhiteSpace.class, PsiComment.class);
    if (PsiUtil.isJavaToken(prev, JavaTokenType.SEMICOLON)) {
      getParent().deleteChildRange(prev, this);
      return;
    }

    super.delete();
  }

  @Override
  public void accept(@Nonnull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitResourceVariable(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @jakarta.annotation.Nonnull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public String toString() {
    return "PsiResourceVariable:" + getName();
  }
}
