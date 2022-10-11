/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.psiutils;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.RelationType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.ast.IElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static consulo.util.lang.ObjectUtil.tryCast;

/**
 * Represents a loop of form {@code for(int/long counter = initializer; counter </<= bound; counter++/--)}
 *
 * @author Tagir Valeev
 */
public class CountingLoop
{
	final
	@Nonnull
  PsiLocalVariable myCounter;
	final
	@Nonnull
  PsiLoopStatement myLoop;
	final
	@Nonnull
	PsiExpression myInitializer;
	final
	@Nonnull
	PsiExpression myBound;
	final boolean myIncluding;
	final boolean myDescending;

	private CountingLoop(@Nonnull PsiLoopStatement loop,
						 @Nonnull PsiLocalVariable counter,
						 @Nonnull PsiExpression initializer,
						 @Nonnull PsiExpression bound,
						 boolean including,
						 boolean descending)
	{
		myInitializer = initializer;
		myCounter = counter;
		myLoop = loop;
		myBound = bound;
		myIncluding = including;
		myDescending = descending;
	}

	/**
	 * @return loop counter variable
	 */
	@Nonnull
	public PsiLocalVariable getCounter()
	{
		return myCounter;
	}

	/**
	 * @return loop statement
	 */
	@Nonnull
	public PsiLoopStatement getLoop()
	{
		return myLoop;
	}

	/**
	 * @return counter variable initial value
	 */
	@Nonnull
	public PsiExpression getInitializer()
	{
		return myInitializer;
	}

	/**
	 * @return loop bound
	 */
	@Nonnull
	public PsiExpression getBound()
	{
		return myBound;
	}

	/**
	 * @return true if bound is including
	 */
	public boolean isIncluding()
	{
		return myIncluding;
	}

	/**
	 * @return true if the loop is descending
	 */
	public boolean isDescending()
	{
		return myDescending;
	}

	@Nullable
	public static CountingLoop from(PsiForStatement forStatement)
	{
		// check that initialization is for(int/long i = <initial_value>;...;...)
		PsiDeclarationStatement initialization = tryCast(forStatement.getInitialization(), PsiDeclarationStatement.class);
		if(initialization == null || initialization.getDeclaredElements().length != 1)
		{
			return null;
		}
		PsiLocalVariable counter = tryCast(initialization.getDeclaredElements()[0], PsiLocalVariable.class);
		if(counter == null)
		{
			return null;
		}
		if(!counter.getType().equals(PsiType.INT) && !counter.getType().equals(PsiType.LONG))
		{
			return null;
		}

		PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(counter.getInitializer());
		if(initializer == null)
		{
			return null;
		}

		// check that increment is like for(...;...;i++)
		boolean descending;
		if(VariableAccessUtils.variableIsIncremented(counter, forStatement.getUpdate()))
		{
			descending = false;
		}
		else if(VariableAccessUtils.variableIsDecremented(counter, forStatement.getUpdate()))
		{
			descending = true;
		}
		else
		{
			return null;
		}

		// check that condition is like for(...;i<bound;...) or for(...;i<=bound;...)
		PsiBinaryExpression condition = tryCast(PsiUtil.skipParenthesizedExprDown(forStatement.getCondition()), PsiBinaryExpression.class);
		if(condition == null)
		{
			return null;
		}
		IElementType type = condition.getOperationTokenType();
		boolean closed = false;
		RelationType relationType = RelationType.fromElementType(type);
		if(relationType == null || !relationType.isInequality())
		{
			return null;
		}
		if(relationType.isSubRelation(RelationType.EQ))
		{
			closed = true;
		}
		if(descending)
		{
			relationType = relationType.getFlipped();
			assert relationType != null;
		}
		PsiExpression bound = ExpressionUtils.getOtherOperand(condition, counter);
		if(bound == null)
		{
			return null;
		}
		if(bound == condition.getLOperand())
		{
			relationType = relationType.getFlipped();
			assert relationType != null;
		}
		if(!relationType.isSubRelation(RelationType.LT))
		{
			return null;
		}
		if(!TypeConversionUtil.areTypesAssignmentCompatible(counter.getType(), bound))
		{
			return null;
		}
		if(VariableAccessUtils.variableIsAssigned(counter, forStatement.getBody()))
		{
			return null;
		}
		return new CountingLoop(forStatement, counter, initializer, bound, closed, descending);
	}
}
