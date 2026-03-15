// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;


import java.util.Arrays;
import java.util.Set;

/**
 * Represents a lattice product of a constant {@link #value} and all {@link #ids}.
 */
final class Component
{
	static final Component[] EMPTY_ARRAY = new Component[0];
	Value value;
	final EKey[] ids;

	Component(Value value, Set<EKey> ids)
	{
		this(value, ids.toArray(new EKey[0]));
	}

	Component(Value value, EKey[] ids)
	{
		this.value = value;
		this.ids = ids;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;

		Component that = (Component) o;

		return value == that.value && Arrays.equals(ids, that.ids);
	}

	@Override
	public int hashCode()
	{
		return 31 * value.hashCode() + Arrays.hashCode(ids);
	}

	public boolean remove(EKey id)
	{
		boolean removed = false;
		for(int i = 0; i < ids.length; i++)
		{
			if(id.equals(ids[i]))
			{
				ids[i] = null;
				removed = true;
			}
		}
		return removed;
	}

	public boolean isEmpty()
	{
		for(EKey id : ids)
		{
			if(id != null)
				return false;
		}
		return true;
	}

	public Component copy()
	{
		return new Component(value, ids.clone());
	}
}