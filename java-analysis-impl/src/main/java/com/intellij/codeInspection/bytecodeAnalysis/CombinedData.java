// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import javax.annotation.Nonnull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.NthParamValue;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.ParamValueBasedDirection;

// additional data structures for combined analysis
interface CombinedData
{

	final class ParamKey
	{
		final Member method;
		final int i;
		final boolean stable;

		ParamKey(Member method, int i, boolean stable)
		{
			this.method = method;
			this.i = i;
			this.stable = stable;
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}

			ParamKey paramKey = (ParamKey) o;

			if(i != paramKey.i)
			{
				return false;
			}
			if(stable != paramKey.stable)
			{
				return false;
			}
			if(!method.equals(paramKey.method))
			{
				return false;
			}

			return true;
		}

		@Override
		public int hashCode()
		{
			int result = method.hashCode();
			result = 31 * result + i;
			result = 31 * result + (stable ? 1 : 0);
			return result;
		}
	}

	// value knowing at which instruction it was created
	interface Trackable
	{
		int getOriginInsnIndex();
	}

	final class TrackableCallValue extends BasicValue implements Trackable
	{
		private final int originInsnIndex;
		final Member method;
		final List<? extends BasicValue> args;
		final boolean stableCall;
		final boolean thisCall;

		TrackableCallValue(int originInsnIndex, Type tp, Member method, List<? extends BasicValue> args, boolean stableCall, boolean thisCall)
		{
			super(tp);
			this.originInsnIndex = originInsnIndex;
			this.method = method;
			this.args = args;
			this.stableCall = stableCall;
			this.thisCall = thisCall;
		}

		@Override
		public int getOriginInsnIndex()
		{
			return originInsnIndex;
		}

		@Nonnull
		Set<EKey> getKeysForParameter(int idx, ParamValueBasedDirection direction)
		{
			Set<EKey> keys = new HashSet<>();
			for(int argI = 0; argI < this.args.size(); argI++)
			{
				BasicValue arg = this.args.get(argI);
				if(arg instanceof NthParamValue)
				{
					NthParamValue npv = (NthParamValue) arg;
					if(npv.n == idx)
					{
						keys.add(new EKey(this.method, direction.withIndex(argI), this.stableCall));
					}
				}
			}
			return keys;
		}
	}

	final class TrackableNullValue extends BasicValue implements Trackable
	{
		static final Type NullType = Type.getObjectType("null");
		private final int originInsnIndex;

		public TrackableNullValue(int originInsnIndex)
		{
			super(NullType);
			this.originInsnIndex = originInsnIndex;
		}

		@Override
		public int getOriginInsnIndex()
		{
			return originInsnIndex;
		}
	}

	final class TrackableValue extends BasicValue implements Trackable
	{
		private final int originInsnIndex;

		public TrackableValue(int originInsnIndex, Type type)
		{
			super(type);
			this.originInsnIndex = originInsnIndex;
		}

		@Override
		public int getOriginInsnIndex()
		{
			return originInsnIndex;
		}
	}

	BasicValue ThisValue = new BasicValue(Type.getObjectType("java/lang/Object"));
}