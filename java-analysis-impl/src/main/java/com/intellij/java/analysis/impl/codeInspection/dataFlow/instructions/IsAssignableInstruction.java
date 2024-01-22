// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nonnull;

/**
 * A binary operation that takes two types from the stack and returns
 * whether one is assignable from another
 */
public class IsAssignableInstruction extends EvalInstruction
{
	public IsAssignableInstruction(PsiMethodCallExpression expression)
	{
		super(expression, 2);
	}

	@Override
	@Nonnull
	public DfaValue eval(@Nonnull DfaValueFactory factory, @Nonnull DfaMemoryState state, @Nonnull DfaValue... arguments)
	{
		PsiType superClass = DfConstantType.getConstantOfType(state.getDfType(arguments[1]), PsiType.class);
		PsiType subClass = DfConstantType.getConstantOfType(state.getDfType(arguments[0]), PsiType.class);
		if(superClass != null && subClass != null)
		{
			TypeConstraint superType = TypeConstraints.instanceOf(superClass);
			TypeConstraint subType = TypeConstraints.instanceOf(subClass);
			if(subType.meet(superType) == TypeConstraints.BOTTOM)
			{
				return factory.getBoolean(false);
			}
			else
			{
				TypeConstraint negated = subType.tryNegate();
				if(negated != null && negated.meet(superType) == TypeConstraints.BOTTOM)
				{
					return factory.getBoolean(true);
				}
			}
		}
		return factory.getUnknown();
	}

	@Override
	public String toString()
	{
		return "IS_ASSIGNABLE_FROM";
	}
}
