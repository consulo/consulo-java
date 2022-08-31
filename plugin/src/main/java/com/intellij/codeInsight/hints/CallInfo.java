package com.intellij.codeInsight.hints;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiParameter;

/**
 * from kotlin
 */
class CallInfo
{
	private final List<CallArgumentInfo> regularArgs;
	private final PsiParameter varArg;
	private final List<PsiExpression> varArgExpressions;

	CallInfo(@Nonnull List<CallArgumentInfo> regularArgs, @javax.annotation.Nullable PsiParameter varArg, @Nonnull List<PsiExpression> varArgExpressions)
	{
		this.regularArgs = regularArgs;
		this.varArg = varArg;
		this.varArgExpressions = varArgExpressions;
	}

	@Nonnull
	public List<CallArgumentInfo> getRegularArgs()
	{
		return regularArgs;
	}

	@Nonnull
	public List<PsiExpression> getVarArgExpressions()
	{
		return varArgExpressions;
	}

	@Nullable
	public PsiParameter getVarArg()
	{
		return varArg;
	}
}
