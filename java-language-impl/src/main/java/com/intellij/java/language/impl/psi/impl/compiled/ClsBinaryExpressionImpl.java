/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.TreeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;

import jakarta.annotation.Nonnull;

class ClsBinaryExpressionImpl extends ClsElementImpl implements PsiBinaryExpression {
  private final ClsElementImpl myParent;
  private final PsiJavaToken myOperator;
  private final PsiExpression myLOperand;
  private final PsiExpression myROperand;

  ClsBinaryExpressionImpl(ClsElementImpl parent, PsiJavaToken sign, PsiExpression left, PsiExpression right) {
    myParent = parent;
    myOperator = new ClsJavaTokenImpl(this, sign.getTokenType(), sign.getText());
    myLOperand = ClsParsingUtil.psiToClsExpression(left, this);
    myROperand = ClsParsingUtil.psiToClsExpression(right, this);
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    buffer.append(getText());
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.BINARY_EXPRESSION);
  }

  @Override
  @RequiredReadAction
  public String getText() {
    return myLOperand.getText() + " " + myOperator.getText() + " " + myROperand.getText();
  }

  @Nonnull
  @Override
  public PsiElement[] getChildren() {
    return new PsiElement[]{
        myLOperand,
        myOperator,
        myROperand
    };
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitBinaryExpression(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Nonnull
  @Override
  public PsiExpression getLOperand() {
    return myLOperand;
  }

  @Nonnull
  @Override
  public PsiExpression getROperand() {
    return myROperand;
  }

  @Nonnull
  @Override
  public PsiJavaToken getOperationSign() {
    return myOperator;
  }

  @Nonnull
  @Override
  public IElementType getOperationTokenType() {
    return myOperator.getTokenType();
  }

  @Override
  public PsiJavaToken getTokenBeforeOperand(@Nonnull PsiExpression operand) {
    return getOperationSign();
  }

  @Override
  public PsiType getType() {
    return myLOperand.getType();
  }

  @Nonnull
  @Override
  public PsiExpression[] getOperands() {
    return new PsiExpression[]{
        getLOperand(),
        getROperand()
    };
  }

  @Override
  public String toString() {
    return "PsiBinaryExpression:" + getText();
  }
}