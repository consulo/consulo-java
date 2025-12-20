/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.parenthesis;

import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import consulo.language.psi.*;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class UnnecessaryParenthesesPredicate implements PsiElementPredicate
{

	public boolean satisfiedBy(PsiElement element)
	{
		if(element instanceof PsiParameterList)
		{
			PsiParameterList parameterList = (PsiParameterList) element;
			PsiElement parent = element.getParent();
			if(parent instanceof PsiLambdaExpression && parameterList.getParametersCount() == 1)
			{
				PsiParameter parameter = parameterList.getParameters()[0];
				return parameter.getTypeElement() == null && element.getFirstChild() != parameter && element.getLastChild() != parameter;
			}
		}
		if(!(element instanceof PsiParenthesizedExpression))
		{
			return false;
		}
		PsiParenthesizedExpression expression = (PsiParenthesizedExpression) element;
		return !ParenthesesUtils.areParenthesesNeeded(expression, false);
	}
}