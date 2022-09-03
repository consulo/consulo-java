/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import com.intellij.psi.*;
import consulo.language.ast.IElementType;
import com.intellij.java.language.psi.util.TypeConversionUtil;

public class VariableAssignedVisitor extends JavaRecursiveElementWalkingVisitor
{

	@Nonnull
	private final Collection<PsiVariable> variables;
	private final boolean recurseIntoClasses;
	private final boolean checkUnaryExpressions;
	private boolean assigned = false;
	private PsiElement excludedElement = null;

	public VariableAssignedVisitor(@Nonnull Collection<PsiVariable> variables, boolean recurseIntoClasses)
	{
		this.variables = variables;
		checkUnaryExpressions = true;
		this.recurseIntoClasses = recurseIntoClasses;
	}

	public VariableAssignedVisitor(@Nonnull PsiVariable variable, boolean recurseIntoClasses)
	{
		variables = Collections.singleton(variable);
		final PsiType type = variable.getType();
		checkUnaryExpressions = TypeConversionUtil.isNumericType(type);
		this.recurseIntoClasses = recurseIntoClasses;
	}

	public VariableAssignedVisitor(@Nonnull PsiVariable variable)
	{
		this(variable, true);
	}

	public void setExcludedElement(PsiElement excludedElement)
	{
		this.excludedElement = excludedElement;
	}

	@Override
	public void visitElement(@Nonnull PsiElement element)
	{
		if(assigned || element == excludedElement)
		{
			return;
		}
		super.visitElement(element);
	}

	@Override
	public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression assignment)
	{
		if(assigned)
		{
			return;
		}
		super.visitAssignmentExpression(assignment);
		final PsiExpression lhs = assignment.getLExpression();
		for(PsiVariable variable : variables)
		{
			if(VariableAccessUtils.evaluatesToVariable(lhs, variable))
			{
				assigned = true;
				break;
			}
		}
	}

	@Override
	public void visitClass(PsiClass aClass)
	{
		if(!recurseIntoClasses || assigned)
		{
			return;
		}
		super.visitClass(aClass);
	}

	@Override
	public void visitPrefixExpression(@Nonnull PsiPrefixExpression prefixExpression)
	{
		if(assigned)
		{
			return;
		}
		super.visitPrefixExpression(prefixExpression);
		if(!checkUnaryExpressions)
		{
			return;
		}
		final IElementType tokenType = prefixExpression.getOperationTokenType();
		if(!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS))
		{
			return;
		}
		final PsiExpression operand = prefixExpression.getOperand();
		for(PsiVariable variable : variables)
		{
			if(VariableAccessUtils.evaluatesToVariable(operand, variable))
			{
				assigned = true;
				break;
			}
		}
	}

	@Override
	public void visitPostfixExpression(@Nonnull PsiPostfixExpression postfixExpression)
	{
		if(assigned)
		{
			return;
		}
		super.visitPostfixExpression(postfixExpression);
		if(!checkUnaryExpressions)
		{
			return;
		}
		final IElementType tokenType = postfixExpression.getOperationTokenType();
		if(!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS))
		{
			return;
		}
		final PsiExpression operand = postfixExpression.getOperand();
		for(PsiVariable variable : variables)
		{
			if(VariableAccessUtils.evaluatesToVariable(operand, variable))
			{
				assigned = true;
				break;
			}
		}
	}

	public boolean isAssigned()
	{
		return assigned;
	}
}