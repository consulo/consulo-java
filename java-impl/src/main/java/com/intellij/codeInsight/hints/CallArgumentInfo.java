package com.intellij.codeInsight.hints;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiParameter;

/**
 * from kotlin
 */
class CallArgumentInfo
{
	private final PsiParameter parameter;
	private final PsiExpression argument;

	CallArgumentInfo(PsiParameter parameter, PsiExpression argument)
	{
		this.parameter = parameter;
		this.argument = argument;
	}

	public PsiParameter getParameter()
	{
		return parameter;
	}

	public PsiExpression getArgument()
	{
		return argument;
	}
}
