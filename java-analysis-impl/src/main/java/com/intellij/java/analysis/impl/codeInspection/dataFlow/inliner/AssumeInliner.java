// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.inliner;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.CFGBuilder;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import jakarta.annotation.Nonnull;

/**
 * JUnit4 Assume.assumeNotNull is a vararg method and each passed null will make the call failing
 */
public class AssumeInliner implements CallInliner
{
	private static final CallMatcher ASSUME_NOT_NULL = CallMatcher.staticCall("org.junit.Assume", "assumeNotNull");

	@Override
	public boolean tryInlineCall(@Nonnull CFGBuilder builder, @Nonnull PsiMethodCallExpression call)
	{
		if(ASSUME_NOT_NULL.test(call) && MethodCallUtils.isVarArgCall(call))
		{
			PsiExpression[] args = call.getArgumentList().getExpressions();
			for(PsiExpression arg : args)
			{
				builder.pushExpression(arg, NullabilityProblemKind.assumeNotNull).pop();
			}
			builder.pushUnknown();
			return true;
		}
		return false;
	}
}
