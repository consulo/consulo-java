/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.actions;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.util.Range;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/13
 */
public class LambdaSmartStepTarget extends SmartStepTarget
{
	private final PsiLambdaExpression myLambda;
	private final int myOrdinal;

	public LambdaSmartStepTarget(
			@Nonnull PsiLambdaExpression lambda,
			@Nullable String label,
			@Nullable PsiElement highlightElement,
			int ordinal,
			Range<Integer> lines)
	{
		super(label, highlightElement, true, lines);
		myLambda = lambda;
		myOrdinal = ordinal;
	}

	public PsiLambdaExpression getLambda()
	{
		return myLambda;
	}

	public int getOrdinal()
	{
		return myOrdinal;
	}

	@Nullable
	@Override
	public Image getIcon()
	{
		return PlatformIconGroup.nodesLambda();
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

		final LambdaSmartStepTarget that = (LambdaSmartStepTarget) o;

		if(myOrdinal != that.myOrdinal)
		{
			return false;
		}
		if(!myLambda.equals(that.myLambda))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = myLambda.hashCode();
		result = 31 * result + myOrdinal;
		return result;
	}
}
