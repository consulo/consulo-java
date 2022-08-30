// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.JavaAnalysisBundle;
import org.jetbrains.annotations.Nls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.function.Supplier;

/**
 * Represents a value nullability within DFA. Unlike {@link Nullability} may have more fine-grained
 * values useful during the DFA. If you have a DfaNullability value (e.g. from {@link CommonDataflow}),
 * and want to check if it's nullable, or not, it's advised to convert it to {@link Nullability} first,
 * as more values could be introduced to this enum in future.
 */
public enum DfaNullability
{
	/**
	 * Means: exactly null
	 */
	NULL("Null", JavaAnalysisBundle.messagePointer("nullability.null"), Nullability.NULLABLE),
	NULLABLE("Nullable", JavaAnalysisBundle.messagePointer("nullability.nullable"), Nullability.NULLABLE),
	NOT_NULL("Not-null", JavaAnalysisBundle.messagePointer("nullability.non.null"), Nullability.NOT_NULL),
	UNKNOWN("Unknown", () -> "", Nullability.UNKNOWN),
	/**
	 * Means: non-stable variable declared as Nullable was checked for nullity and flushed afterwards (e.g. by unknown method call),
	 * so we are unsure about its nullability anymore.
	 */
	FLUSHED("Flushed", () -> "", Nullability.UNKNOWN);

	private final
	@Nonnull
	String myInternalName;
	private final
	@Nonnull
	Supplier<String> myPresentationalName;
	private final
	@Nonnull
	Nullability myNullability;

	DfaNullability(@Nonnull String internalName, @Nonnull Supplier< String> presentationalName, @Nonnull Nullability nullability)
	{
		myInternalName = internalName;
		myPresentationalName = presentationalName;
		myNullability = nullability;
	}

	@Nonnull
	public String getInternalName()
	{
		return myInternalName;
	}

	public
	@Nonnull
	@Nls
	String getPresentationName()
	{
		return myPresentationalName.get();
	}

	@Nonnull
	public DfaNullability unite(@Nonnull DfaNullability other)
	{
		if(this == other)
		{
			return this;
		}
		if(this == NULL || other == NULL ||
				this == NULLABLE || other == NULLABLE)
		{
			return NULLABLE;
		}
		if(this == FLUSHED || other == FLUSHED)
		{
			return FLUSHED;
		}
		return UNKNOWN;
	}

	@Nullable
	public DfaNullability intersect(@Nonnull DfaNullability right)
	{
		if(this == NOT_NULL)
		{
			return right == NULL ? null : NOT_NULL;
		}
		if(right == NOT_NULL)
		{
			return this == NULL ? null : NOT_NULL;
		}
		if(this == UNKNOWN)
		{
			return right;
		}
		if(right == UNKNOWN)
		{
			return this;
		}
		if(this == FLUSHED && toNullability(right) == Nullability.NULLABLE ||
				right == FLUSHED && toNullability(this) == Nullability.NULLABLE)
		{
			return NULLABLE;
		}
		return equals(right) ? this : null;
	}

	@Nonnull
	public static Nullability toNullability(@Nullable DfaNullability dfaNullability)
	{
		return dfaNullability == null ? Nullability.UNKNOWN : dfaNullability.myNullability;
	}

	@Nonnull
	public static DfaNullability fromNullability(@Nonnull Nullability nullability)
	{
		switch(nullability)
		{
			case NOT_NULL:
				return NOT_NULL;
			case NULLABLE:
				return NULLABLE;
			case UNKNOWN:
				return UNKNOWN;
		}
		throw new IllegalStateException("Unknown nullability: " + nullability);
	}

	@Nonnull
	public DfReferenceType asDfType()
	{
		switch(this)
		{
			case NULL:
				return DfTypes.NULL;
			case NOT_NULL:
				return DfTypes.NOT_NULL_OBJECT;
			case UNKNOWN:
				return DfTypes.OBJECT_OR_NULL;
			default:
				return DfTypes.customObject(TypeConstraints.TOP, this, Mutability.UNKNOWN, null, DfTypes.BOTTOM);
		}
	}

	@Nonnull
	public static DfaNullability fromDfType(@Nonnull DfType type)
	{
		return type instanceof DfReferenceType ? ((DfReferenceType) type).getNullability() : UNKNOWN;
	}
}
