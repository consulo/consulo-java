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

import javax.annotation.Nonnull;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPrefixExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;

class ClsPrefixExpressionImpl extends ClsElementImpl implements PsiPrefixExpression
{
	private final ClsElementImpl myParent;
	private final PsiJavaToken myOperator;
	private final PsiExpression myOperand;

	ClsPrefixExpressionImpl(ClsElementImpl parent, PsiJavaToken sign, PsiExpression operand)
	{
		myParent = parent;
		myOperator = new ClsJavaTokenImpl(this, sign.getTokenType(), sign.getText());
		myOperand = ClsParsingUtil.psiToClsExpression(operand, this);
	}

	@Nonnull
	@Override
	public PsiExpression getOperand()
	{
		return myOperand;
	}

	@Nonnull
	@Override
	public PsiJavaToken getOperationSign()
	{
		return myOperator;
	}

	@Nonnull
	@Override
	public IElementType getOperationTokenType()
	{
		return myOperator.getTokenType();
	}

	@Override
	public PsiType getType()
	{
		return myOperand.getType();
	}

	@Override
	public PsiElement getParent()
	{
		return myParent;
	}

	@Nonnull
	@Override
	public PsiElement[] getChildren()
	{
		return new PsiElement[]{
				myOperator,
				myOperand
		};
	}

	@Override
	public String getText()
	{
		return StringUtil.join(myOperator.getText(), myOperand.getText());
	}

	@Override
	public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer)
	{
		buffer.append(getText());
	}

	@Override
	public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException
	{
		setMirrorCheckingType(element, JavaElementType.PREFIX_EXPRESSION);
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitPrefixExpression(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public String toString()
	{
		return "PsiPrefixExpression:" + getText();
	}
}