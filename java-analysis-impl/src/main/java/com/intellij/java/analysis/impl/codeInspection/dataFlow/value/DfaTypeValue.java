// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.value;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiType;
import org.jetbrains.annotations.Contract;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DfaTypeValue extends DfaValue
{
	private final
	@Nonnull
	DfType myType;

	DfaTypeValue(@Nonnull DfaValueFactory factory, @Nonnull DfType type)
	{
		super(factory);
		myType = type;
	}

	@Nonnull
	@Override
	public DfType getDfType()
	{
		return myType;
	}

	@Nullable
	@Override
	public PsiType getType()
	{
		return toPsiType(myFactory.getProject(), myType);
	}

	public static boolean isUnknown(DfaValue value)
	{
		return value instanceof DfaTypeValue && value.getDfType() == DfTypes.TOP;
	}

	@Override
	public String toString()
	{
		return myType.toString();
	}

	/**
	 * Checks whether given value is a special value representing method failure, according to its contract
	 *
	 * @param value value to check
	 * @return true if specified value represents method failure
	 */
	@Contract("null -> false")
	public static boolean isContractFail(DfaValue value)
	{
		return value instanceof DfaTypeValue && value.getDfType() == DfTypes.FAIL;
	}

	static class Factory
	{
		private final DfaValueFactory myFactory;
		private final Map<DfType, DfaTypeValue> myValues = new HashMap<>();

		Factory(DfaValueFactory factory)
		{
			myFactory = factory;
		}

		@Nonnull
		DfaTypeValue create(@Nonnull DfType type)
		{
			return myValues.computeIfAbsent(type, t -> new DfaTypeValue(myFactory, t));
		}
	}

	@Nullable
	public static PsiType toPsiType(Project project, DfType dfType)
	{
		if(dfType instanceof DfPrimitiveType)
		{
			return ((DfPrimitiveType) dfType).getPsiType();
		}
		if(dfType instanceof DfReferenceType)
		{
			return ((DfReferenceType) dfType).getConstraint().getPsiType(project);
		}
		return null;
	}
}
