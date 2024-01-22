package com.intellij.java.impl.codeInsight.hints;

import java.util.List;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiParameter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * from kotlin
 */
class CallInfo
{
	private final List<CallArgumentInfo> regularArgs;
	private final PsiParameter varArg;
	private final List<PsiExpression> varArgExpressions;

	CallInfo(@jakarta.annotation.Nonnull List<CallArgumentInfo> regularArgs, @Nullable PsiParameter varArg, @jakarta.annotation.Nonnull List<PsiExpression> varArgExpressions)
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

	@jakarta.annotation.Nonnull
	public List<PsiExpression> getVarArgExpressions()
	{
		return varArgExpressions;
	}

	@jakarta.annotation.Nullable
	public PsiParameter getVarArg()
	{
		return varArg;
	}
}
