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
package com.intellij.codeInsight.guess.impl;

import com.intellij.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.DfaFactMap;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.value.DfaInstanceofValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.util.containers.MultiMap;
import consulo.logging.Logger;
import consulo.util.collection.HashingStrategy;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * @author peter
 */
public class ExpressionTypeMemoryState extends DfaMemoryStateImpl
{
	private static final Logger LOG = Logger.getInstance(ExpressionTypeMemoryState.class);
	public static final HashingStrategy<PsiExpression> EXPRESSION_HASHING_STRATEGY = new HashingStrategy<PsiExpression>()
	{
		@Override
		public int hashCode(PsiExpression object)
		{
			if(object instanceof PsiReferenceExpression)
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
		public boolean equals(@Nonnull PsiExpression o1, @Nonnull PsiExpression o2)
		{
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
	};
	private final boolean myHonorAssignments;
	// may be shared between memory state instances
	private MultiMap<PsiExpression, PsiType> myStates;

	public ExpressionTypeMemoryState(final DfaValueFactory factory, boolean honorAssignments)
	{
		super(factory);
		myHonorAssignments = honorAssignments;
		myStates = MultiMap.createSet(EXPRESSION_HASHING_STRATEGY);
	}

	private ExpressionTypeMemoryState(ExpressionTypeMemoryState toCopy)
	{
		super(toCopy);
		myHonorAssignments = toCopy.myHonorAssignments;
		myStates = toCopy.myStates;
	}

	@Override
	protected DfaFactMap filterFactsOnAssignment(DfaVariableValue var, @Nonnull DfaFactMap facts)
	{
		if(myHonorAssignments)
		{
			return facts;
		}
		if(ControlFlowAnalyzer.isTempVariable(var) ||
				var.getPsiVariable() instanceof PsiParameter && var.getPsiVariable().getParent().getParent() instanceof PsiLambdaExpression)
		{
			// Pass type normally for synthetic lambda parameter assignment
			return facts;
		}
		return facts.with(DfaFactType.TYPE_CONSTRAINT, null);
	}

	@Nonnull
	@Override
	public DfaMemoryStateImpl createCopy()
	{
		return new ExpressionTypeMemoryState(this);
	}

	@Override
	public boolean isSuperStateOf(DfaMemoryStateImpl that)
	{
		if(!super.isSuperStateOf(that))
		{
			return false;
		}
		MultiMap<PsiExpression, PsiType> thatStates = ((ExpressionTypeMemoryState) that).myStates;
		if(thatStates == myStates)
		{
			return true;
		}
		for(Map.Entry<PsiExpression, Collection<PsiType>> entry : myStates.entrySet())
		{
			Collection<PsiType> thisTypes = entry.getValue();
			Collection<PsiType> thatTypes = thatStates.get(entry.getKey());
			if(!thatTypes.containsAll(thisTypes))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean applyCondition(DfaValue dfaCond)
	{
		if(dfaCond instanceof DfaInstanceofValue)
		{
			final DfaInstanceofValue value = (DfaInstanceofValue) dfaCond;
			if(!value.isNegated())
			{
				setExpressionType(value.getExpression(), value.getCastType());
			}
			return super.applyCondition(((DfaInstanceofValue) dfaCond).getRelation());
		}

		return super.applyCondition(dfaCond);
	}

	MultiMap<PsiExpression, PsiType> getStates()
	{
		return myStates;
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
		if(!super.equals(o))
		{
			return false;
		}

		ExpressionTypeMemoryState that = (ExpressionTypeMemoryState) o;
		return myStates.equals(that.myStates);
	}

	@Override
	public int hashCode()
	{
		int result = super.hashCode();
		result = 31 * result + myStates.hashCode();
		return result;
	}

	@Override
	public String toString()
	{
		return super.toString() + " states=[" + myStates + "]";
	}

	void removeExpressionType(@Nonnull PsiExpression expression)
	{
		if(myStates.containsKey(expression))
		{
			MultiMap<PsiExpression, PsiType> oldStates = myStates;
			myStates = MultiMap.createSet(EXPRESSION_HASHING_STRATEGY);
			for(Map.Entry<PsiExpression, Collection<PsiType>> entry : oldStates.entrySet())
			{
				if(!EXPRESSION_HASHING_STRATEGY.equals(entry.getKey(), expression))
				{
					myStates.putValues(entry.getKey(), entry.getValue());
				}
			}
		}
	}

	void setExpressionType(@Nonnull PsiExpression expression, @Nonnull PsiType type)
	{
		if(!myStates.get(expression).contains(type))
		{
			MultiMap<PsiExpression, PsiType> oldStates = myStates;
			myStates = MultiMap.createSet(EXPRESSION_HASHING_STRATEGY);
			myStates.putAllValues(oldStates);
			myStates.putValue(expression, type);
		}
	}
}
