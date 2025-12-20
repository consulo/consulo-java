/*
 * Copyright 2009-2014 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.forloop;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import jakarta.annotation.Nullable;

class ReverseForLoopDirectionPredicate implements PsiElementPredicate
{

	public boolean satisfiedBy(PsiElement element)
	{
		if(!(element instanceof PsiJavaToken))
		{
			return false;
		}
		PsiJavaToken keyword = (PsiJavaToken) element;
		IElementType tokenType = keyword.getTokenType();
		if(!JavaTokenType.FOR_KEYWORD.equals(tokenType))
		{
			return false;
		}
		PsiElement parent = keyword.getParent();
		if(!(parent instanceof PsiForStatement))
		{
			return false;
		}
		PsiForStatement forStatement = (PsiForStatement) parent;
		PsiStatement initialization = forStatement.getInitialization();
		if(!(initialization instanceof PsiDeclarationStatement))
		{
			return false;
		}
		PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) initialization;
		PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
		if(declaredElements.length != 1)
		{
			return false;
		}
		PsiElement declaredElement = declaredElements[0];
		if(!(declaredElement instanceof PsiLocalVariable))
		{
			return false;
		}
		PsiVariable variable = (PsiVariable) declaredElement;
		PsiType type = variable.getType();
		if(!PsiType.INT.equals(type) && !PsiType.LONG.equals(type))
		{
			return false;
		}
		PsiExpression condition = forStatement.getCondition();
		if(!isVariableCompared(variable, condition))
		{
			return false;
		}
		PsiStatement update = forStatement.getUpdate();
		return isVariableIncrementOrDecremented(variable, update);
	}

	public static boolean isVariableCompared(@Nonnull PsiVariable variable, @Nullable PsiExpression expression)
	{
		if(!(expression instanceof PsiBinaryExpression))
		{
			return false;
		}
		PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
		IElementType tokenType = binaryExpression.getOperationTokenType();
		if(!ComparisonUtils.isComparisonOperation(tokenType))
		{
			return false;
		}
		PsiExpression lhs = binaryExpression.getLOperand();
		PsiExpression rhs = binaryExpression.getROperand();
		if(rhs == null)
		{
			return false;
		}
		if(VariableAccessUtils.evaluatesToVariable(lhs, variable))
		{
			return true;
		}
		else if(VariableAccessUtils.evaluatesToVariable(rhs, variable))
		{
			return true;
		}
		return false;
	}

	public static boolean isVariableIncrementOrDecremented(@Nonnull PsiVariable variable, @Nullable PsiStatement statement)
	{
		if(!(statement instanceof PsiExpressionStatement))
		{
			return false;
		}
		PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
		PsiExpression expression = expressionStatement.getExpression();
		expression = ParenthesesUtils.stripParentheses(expression);
		if(expression instanceof PsiPrefixExpression)
		{
			PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
			IElementType tokenType = prefixExpression.getOperationTokenType();
			if(!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS))
			{
				return false;
			}
			PsiExpression operand = prefixExpression.getOperand();
			return VariableAccessUtils.evaluatesToVariable(operand, variable);
		}
		else if(expression instanceof PsiPostfixExpression)
		{
			PsiPostfixExpression postfixExpression = (PsiPostfixExpression) expression;
			IElementType tokenType = postfixExpression.getOperationTokenType();
			if(!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS))
			{
				return false;
			}
			PsiExpression operand = postfixExpression.getOperand();
			return VariableAccessUtils.evaluatesToVariable(operand, variable);
		}
		else if(expression instanceof PsiAssignmentExpression)
		{
			PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expression;
			IElementType tokenType = assignmentExpression.getOperationTokenType();
			PsiExpression lhs = assignmentExpression.getLExpression();
			lhs = ParenthesesUtils.stripParentheses(lhs);
			if(!VariableAccessUtils.evaluatesToVariable(lhs, variable))
			{
				return false;
			}
			PsiExpression rhs = assignmentExpression.getRExpression();
			rhs = ParenthesesUtils.stripParentheses(rhs);
			if(tokenType == JavaTokenType.EQ)
			{
				if(!(rhs instanceof PsiBinaryExpression))
				{
					return false;
				}
				PsiBinaryExpression binaryExpression = (PsiBinaryExpression) rhs;
				IElementType token = binaryExpression.getOperationTokenType();
				if(!token.equals(JavaTokenType.PLUS) && !token.equals(JavaTokenType.MINUS))
				{
					return false;
				}
				PsiExpression lOperand = binaryExpression.getLOperand();
				lOperand = ParenthesesUtils.stripParentheses(lOperand);
				PsiExpression rOperand = binaryExpression.getROperand();
				rOperand = ParenthesesUtils.stripParentheses(rOperand);
				if(VariableAccessUtils.evaluatesToVariable(rOperand, variable))
				{
					return true;
				}
				else if(VariableAccessUtils.evaluatesToVariable(lOperand, variable))
				{
					return true;
				}
			}
			else if(tokenType == JavaTokenType.PLUSEQ || tokenType == JavaTokenType.MINUSEQ)
			{
				return true;
			}
		}
		return false;
	}
}