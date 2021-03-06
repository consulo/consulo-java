/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import javax.annotation.Nonnull;

/**
 * @author peter
 */
public class DfaInstanceofValue extends DfaValue
{
	private final
	@Nonnull
	PsiExpression myExpression;
	private final
	@Nonnull
	PsiType myCastType;
	private final boolean myNegated;
	private final
	@Nonnull
	DfaValue myRelation;

	public DfaInstanceofValue(DfaValueFactory factory,
							  @Nonnull PsiExpression expression,
							  @Nonnull PsiType castType,
							  @Nonnull DfaValue relation,
							  boolean negated)
	{
		super(factory);
		myExpression = expression;
		myCastType = castType;
		myRelation = relation;
		myNegated = negated;
	}

	@Nonnull
	public DfaValue getRelation()
	{
		return myRelation;
	}

	@Nonnull
	public PsiExpression getExpression()
	{
		return myExpression;
	}

	@Nonnull
	public PsiType getCastType()
	{
		return myCastType;
	}

	public boolean isNegated()
	{
		return myNegated;
	}

	@Override
	public DfaValue createNegated()
	{
		return new DfaInstanceofValue(myFactory, myExpression, myCastType, myRelation.createNegated(), !myNegated);
	}
}
