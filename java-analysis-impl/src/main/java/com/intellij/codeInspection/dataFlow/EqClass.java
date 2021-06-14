// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.util.ObjectUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author peter
 */
class EqClass extends SortedIntSet implements Iterable<DfaValue>
{
	private final DfaValueFactory myFactory;

	/**
	 * A comparator which allows to select a "canonical" variable of several variables (which is minimal of them).
	 * Variables with shorter qualifier chain are preferred to be canonical.
	 */
	static final Comparator<DfaVariableValue> CANONICAL_VARIABLE_COMPARATOR =
			Comparator.nullsFirst((v1, v2) -> {
				int result = EqClass.CANONICAL_VARIABLE_COMPARATOR.compare(v1.getQualifier(), v2.getQualifier());
				if(result != 0)
				{
					return result;
				}
				return Integer.compare(v1.getID(), v2.getID());
			});

	EqClass(DfaValueFactory factory)
	{
		myFactory = factory;
	}

	EqClass(@Nonnull EqClass toCopy)
	{
		super(toCopy.toNativeArray());
		myFactory = toCopy.myFactory;
	}

	@Override
	public String toString()
	{
		StringBuilder buf = new StringBuilder();
		buf.append("(");
		for(int i = 0; i < size(); i++)
		{
			if(i > 0)
			{
				buf.append(", ");
			}
			int value = get(i);
			DfaValue dfaValue = myFactory.getValue(value);
			buf.append(dfaValue);
		}
		buf.append(")");
		return buf.toString();
	}

	List<DfaVariableValue> getVariables(boolean unwrap)
	{
		List<DfaVariableValue> vars = new ArrayList<>();
		forValues(id -> {
			DfaValue value = myFactory.getValue(id);
			if(value instanceof DfaVariableValue)
			{
				vars.add((DfaVariableValue) value);
			}
			else if(unwrap && value instanceof DfaBoxedValue)
			{
				vars.add(((DfaBoxedValue) value).getWrappedValue());
			}
		});
		return vars;
	}

	/**
	 * @return the "canonical" variable for this class (according to {@link #CANONICAL_VARIABLE_COMPARATOR}) or
	 * null if the class does not contain variables.
	 */
	@Nullable
	DfaVariableValue getCanonicalVariable()
	{
		if(size() == 1)
		{
			return ObjectUtils.tryCast(myFactory.getValue(get(0)), DfaVariableValue.class);
		}
		return IntStreamEx.range(size()).mapToObj(idx -> myFactory.getValue(get(idx)))
				.select(DfaVariableValue.class).min(CANONICAL_VARIABLE_COMPARATOR).orElse(null);
	}

	List<DfaValue> getMemberValues()
	{
		final List<DfaValue> result = new ArrayList<>(size());
		forValues(id -> {
			DfaValue value = myFactory.getValue(id);
			result.add(value);
		});
		return result;
	}

	@Nullable
	DfaConstValue findConstant()
	{
		return StreamEx.of(iterator()).map(it -> it instanceof DfaConstValue ? (DfaConstValue)it : null).filter(Objects::nonNull).findFirst().orElse(null);
	}

	boolean containsConstantsOnly()
	{
		int size = size();
		return size <= 1 && (size == 0 || myFactory.getValue(get(0)) instanceof DfaConstValue);
	}

	@Nonnull
	@Override
	public Iterator<DfaValue> iterator()
	{
		return new Iterator<>()
		{
			int pos;

			@Override
			public boolean hasNext()
			{
				return pos < size();
			}

			@Override
			public DfaValue next()
			{
				if(pos >= size())
				{
					throw new NoSuchElementException();
				}
				return myFactory.getValue(get(pos++));
			}
		};
	}
}
