// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.guess.impl;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaMemoryStateImpl;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiParameter;
import jakarta.annotation.Nonnull;

/**
 * A memory state that may ignore type constraint known from assignment.
 * Useful for completion. E.g. if {@code Collection<?> c = new ArrayList<>();} was created
 * it might be undesired to suggest List-specific methods on e.g. {@code c.add} completion.
 */
public class AssignmentFilteringMemoryState extends DfaMemoryStateImpl
{
	/**
	 * @param factory factory to use
	 */
	public AssignmentFilteringMemoryState(DfaValueFactory factory)
	{
		super(factory);
	}

	public AssignmentFilteringMemoryState(AssignmentFilteringMemoryState toCopy)
	{
		super(toCopy);
	}

	@Override
	protected DfType filterDfTypeOnAssignment(DfaVariableValue var, @Nonnull DfType dfType)
	{
		if(ControlFlowAnalyzer.isTempVariable(var) || (!(dfType instanceof DfReferenceType)) ||
				var.getPsiVariable() instanceof PsiParameter && var.getPsiVariable().getParent().getParent() instanceof PsiLambdaExpression)
		{
			// Pass type normally for synthetic lambda parameter assignment
			return dfType;
		}
		return ((DfReferenceType) dfType).dropTypeConstraint();
	}
}
