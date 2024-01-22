/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiExpression;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.annotation.access.RequiredReadAction;

import jakarta.annotation.Nullable;

/**
 * from kotlin
 */
class ExpressionRange
{
	static ExpressionRange create(LighterASTNode expr, int scopeStart)
	{
		return new ExpressionRange(expr.getStartOffset() - scopeStart, expr.getEndOffset() - scopeStart);
	}

	private int startOffset;
	private int endOffset;

	public ExpressionRange(int startOffset, int endOffset)
	{
		this.startOffset = startOffset;
		this.endOffset = endOffset;
	}

	public int getStartOffset()
	{
		return startOffset;
	}

	public int getEndOffset()
	{
		return endOffset;
	}

	@Nullable
	@RequiredReadAction
	public PsiExpression restoreExpression(PsiCodeBlock scope)
	{
		int scopeStart = scope.getTextRange().getStartOffset();

		return PsiTreeUtil.findElementOfClassAtRange(scope.getContainingFile(), startOffset + scopeStart, endOffset + scopeStart, PsiExpression.class);
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		ExpressionRange that = (ExpressionRange) o;

		if(startOffset != that.startOffset)
		{
			return false;
		}
		if(endOffset != that.endOffset)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = startOffset;
		result = 31 * result + endOffset;
		return result;
	}

	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder("ExpressionRange{");
		sb.append("startOffset=").append(startOffset);
		sb.append(", endOffset=").append(endOffset);
		sb.append('}');
		return sb.toString();
	}
}
