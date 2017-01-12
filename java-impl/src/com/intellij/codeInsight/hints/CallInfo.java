package com.intellij.codeInsight.hints;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParameter;

/**
 * from kotlin
 */
class CallInfo
{
	private final List<CallArgumentInfo> regularArgs;
	private final PsiParameter varArg;
	private final List<PsiExpression> varArgExpressions;

	CallInfo(@NotNull List<CallArgumentInfo> regularArgs, @Nullable PsiParameter varArg, @NotNull List<PsiExpression> varArgExpressions)
	{
		this.regularArgs = regularArgs;
		this.varArg = varArg;
		this.varArgExpressions = varArgExpressions;
	}

	@NotNull
	public List<CallArgumentInfo> getRegularArgs()
	{
		return regularArgs;
	}

	@NotNull
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
