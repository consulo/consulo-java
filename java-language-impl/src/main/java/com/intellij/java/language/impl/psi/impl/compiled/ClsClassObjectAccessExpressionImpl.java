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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;

import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class ClsClassObjectAccessExpressionImpl extends ClsElementImpl implements PsiClassObjectAccessExpression {
  private final ClsElementImpl myParent;
  private final ClsTypeElementImpl myTypeElement;

  public ClsClassObjectAccessExpressionImpl(ClsElementImpl parent, String canonicalClassText) {
    myParent = parent;
    myTypeElement = new ClsTypeElementImpl(this, canonicalClassText, ClsTypeElementImpl.VARIANCE_NONE);
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    myTypeElement.appendMirrorText(0, buffer);
    buffer.append('.').append(PsiKeyword.CLASS);
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirror(getOperand(), SourceTreeToPsiMap.<PsiClassObjectAccessExpression>treeToPsiNotNull(element).getOperand());
  }

  @Override
  @Nonnull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myTypeElement};
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClassObjectAccessExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  @Nonnull
  public PsiTypeElement getOperand() {
    return myTypeElement;
  }

  @Override
  public PsiType getType() {
    return PsiImplUtil.getType(this);
  }

  @Override
  public String getText() {
    final StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }
}
