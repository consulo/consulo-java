/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiResourceExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;

public class PsiResourceExpressionImpl extends CompositePsiElement implements PsiResourceExpression
{
	public PsiResourceExpressionImpl()
	{
		super(JavaElementType.RESOURCE_EXPRESSION);
	}

	@Nonnull
	@Override
	public PsiExpression getExpression()
	{
		return (PsiExpression) getFirstChild();
	}

	@Nullable
	@Override
	public PsiType getType()
	{
		return getExpression().getType();
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitResourceExpression(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public String toString()
	{
		return "PsiResourceExpression";
	}
}
