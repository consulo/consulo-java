// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiSwitchExpression;
import com.intellij.java.language.psi.PsiYieldStatement;
import com.intellij.psi.*;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PsiYieldStatementImpl extends CompositePsiElement implements PsiYieldStatement
{
	public PsiYieldStatementImpl()
	{
		super(JavaElementType.YIELD_STATEMENT);
	}

	@Override
	public PsiExpression getExpression()
	{
		return (PsiExpression) findPsiChildByType(ElementType.EXPRESSION_BIT_SET);
	}

	@Nullable
	@Override
	public PsiSwitchExpression findEnclosingExpression()
	{
		return PsiImplUtil.findEnclosingSwitchExpression(this);
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitYieldStatement(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public String toString()
	{
		return "PsiYieldStatement";
	}
}