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

package com.intellij.codeInspection.bytecodeAnalysis;

import one.util.streamex.StreamEx;

import java.util.Arrays;
import java.util.stream.Stream;

abstract class EffectQuantum
{
	private final int myHash;

	EffectQuantum(int hash)
	{
		myHash = hash;
	}

	Stream<EKey> dependencies()
	{
		return Stream.empty();
	}

	@Override
	public final int hashCode()
	{
		return myHash;
	}

	static final EffectQuantum TopEffectQuantum = new EffectQuantum(-1)
	{
		@Override
		public String toString()
		{
			return "Top";
		}
	};
	static final EffectQuantum ThisChangeQuantum = new EffectQuantum(-2)
	{
		@Override
		public String toString()
		{
			return "Changes this";
		}
	};

	static final class FieldReadQuantum extends EffectQuantum
	{
		final EKey key;

		FieldReadQuantum(EKey key)
		{
			super(key.hashCode());
			this.key = key;
		}

		@Override
		Stream<EKey> dependencies()
		{
			return Stream.of(key);
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
				return true;
			return o != null && getClass() == o.getClass() && key == ((FieldReadQuantum) o).key;
		}

		@Override
		public String toString()
		{
			return "Reads field " + key;
		}
	}

	static final class ReturnChangeQuantum extends EffectQuantum
	{
		final EKey key;

		ReturnChangeQuantum(EKey key)
		{
			super(key.hashCode());
			this.key = key;
		}

		@Override
		Stream<EKey> dependencies()
		{
			return Stream.of(key);
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
				return true;
			return o != null && getClass() == o.getClass() && key == ((ReturnChangeQuantum) o).key;
		}

		@Override
		public String toString()
		{
			return "Changes return value of " + key;
		}
	}

	static final class ParamChangeQuantum extends EffectQuantum
	{
		final int n;

		ParamChangeQuantum(int n)
		{
			super(n);
			this.n = n;
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
				return true;
			return o != null && getClass() == o.getClass() && n == ((ParamChangeQuantum) o).n;
		}

		@Override
		public String toString()
		{
			return "Changes param#" + n;
		}
	}

	static final class CallQuantum extends EffectQuantum
	{
		final EKey key;
		final DataValue[] data;
		final boolean isStatic;

		CallQuantum(EKey key, DataValue[] data, boolean isStatic)
		{
			super((key.hashCode() * 31 + Arrays.hashCode(data)) * 31 + (isStatic ? 1 : 0));
			this.key = key;
			this.data = data;
			this.isStatic = isStatic;
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
				return true;
			if(o == null || getClass() != o.getClass())
				return false;

			CallQuantum that = (CallQuantum) o;

			if(isStatic != that.isStatic)
				return false;
			if(!key.equals(that.key))
				return false;
			if(!Arrays.equals(data, that.data))
				return false;
			return true;
		}

		@Override
		Stream<EKey> dependencies()
		{
			return StreamEx.of(data).flatMap(DataValue::dependencies).prepend(key);
		}

		@Override
		public String toString()
		{
			return "Calls " + key;
		}
	}
}