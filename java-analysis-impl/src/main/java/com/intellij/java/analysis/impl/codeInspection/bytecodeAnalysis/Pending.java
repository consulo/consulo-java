/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

final class Pending implements Result
{
	@Nonnull
	final Component[] delta; // sum

	Pending(Collection<Component> delta)
	{
		this(delta.toArray(Component.EMPTY_ARRAY));
	}

	Pending(@Nonnull Component[] delta)
	{
		this.delta = delta;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		return Arrays.equals(delta, ((Pending) o).delta);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(delta);
	}

	@Nonnull
	Pending copy()
	{
		Component[] copy = new Component[delta.length];
		for(int i = 0; i < delta.length; i++)
		{
			copy[i] = delta[i].copy();
		}
		return new Pending(copy);
	}

	@Override
	public Stream<EKey> dependencies()
	{
		return Arrays.stream(delta).flatMap(component -> Stream.of(component.ids));
	}

	@Override
	public String toString()
	{
		return "Pending[" + delta.length + "]";
	}
}
