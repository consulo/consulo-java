// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.guess.impl;

import com.intellij.java.analysis.impl.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.VariableDescriptor;
import consulo.logging.Logger;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.util.collection.HashingStrategy;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.Objects;

public final class ExpressionVariableDescriptor implements VariableDescriptor
{
	public static final HashingStrategy<PsiExpression> EXPRESSION_HASHING_STRATEGY = new PsiExpressionStrategy();

	private final
	@jakarta.annotation.Nonnull
	PsiExpression myExpression;

	public ExpressionVariableDescriptor(@Nonnull PsiExpression expression)
	{
		myExpression = expression;
	}

	@Override
	public boolean isStable()
	{
		return true;
	}

	public
	@Nonnull
	PsiExpression getExpression()
	{
		return myExpression;
	}

	@Override
	public
	@Nullable
	PsiType getType(@jakarta.annotation.Nullable DfaVariableValue qualifier)
	{
		return myExpression.getType();
	}

	@Override
	public int hashCode()
	{
		return EXPRESSION_HASHING_STRATEGY.hashCode(myExpression);
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof ExpressionVariableDescriptor &&
				EXPRESSION_HASHING_STRATEGY.equals(myExpression, ((ExpressionVariableDescriptor) obj).myExpression);
	}

	@Override
	public String toString()
	{
		return myExpression.getText();
	}

	private static class PsiExpressionStrategy implements HashingStrategy<PsiExpression>
	{
		private static final Logger LOG = Logger.getInstance(PsiExpressionStrategy.class);

		@Override
		public int hashCode(PsiExpression object)
		{
			if(object == null)
			{
				return 0;
			}
			else if(object instanceof PsiReferenceExpression)
			{
				return Objects.hashCode(((PsiReferenceExpression) object).getReferenceName()) * 31 + 1;
			}
			else if(object instanceof PsiMethodCallExpression)
			{
				return Objects.hashCode(((PsiMethodCallExpression) object).getMethodExpression().getReferenceName()) * 31 + 2;
			}
			return object.getNode().getElementType().hashCode();
		}

		@Override
		public boolean equals(PsiExpression o1, PsiExpression o2)
		{
			if(o1 == o2)
			{
				return true;
			}
			if(o1 == null || o2 == null)
			{
				return false;
			}
			if(JavaPsiEquivalenceUtil.areExpressionsEquivalent(o1, o2))
			{
				if(hashCode(o1) != hashCode(o2))
				{
					LOG.error("different hashCodes: " + o1 + "; " + o2 + "; " + hashCode(o1) + "!=" + hashCode(o2));
				}
				return true;
			}
			return false;
		}
	}
}
