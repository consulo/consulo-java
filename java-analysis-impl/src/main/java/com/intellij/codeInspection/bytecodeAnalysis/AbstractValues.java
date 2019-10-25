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

import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;

import java.util.Set;

class AbstractValues
{
	static final class ParamValue extends BasicValue
	{
		ParamValue(Type tp)
		{
			super(tp);
		}
	}

	static final BasicValue InstanceOfCheckValue = new BasicValue(Type.INT_TYPE)
	{
		@Override
		public boolean equals(Object value)
		{
			return this == value;
		}
	};

	static final BasicValue TrueValue = new BasicValue(Type.INT_TYPE)
	{
		@Override
		public boolean equals(Object value)
		{
			return this == value;
		}
	};

	static final BasicValue FalseValue = new BasicValue(Type.INT_TYPE)
	{
		@Override
		public boolean equals(Object value)
		{
			return this == value;
		}
	};

	static final BasicValue NullValue = new BasicValue(Type.getObjectType("null"))
	{
		@Override
		public boolean equals(Object value)
		{
			return this == value;
		}
	};

	static final class NotNullValue extends BasicValue
	{
		NotNullValue(Type tp)
		{
			super(tp);
		}
	}

	static final class CallResultValue extends BasicValue
	{
		final Set<EKey> inters;

		CallResultValue(Type tp, Set<EKey> inters)
		{
			super(tp);
			this.inters = inters;
		}
	}

	static final class NthParamValue extends BasicValue
	{
		final int n;

		NthParamValue(Type type, int n)
		{
			super(type);
			this.n = n;
		}
	}

	static final BasicValue CLASS_VALUE = new NotNullValue(Type.getObjectType("java/lang/Class"));
	static final BasicValue METHOD_VALUE = new NotNullValue(Type.getObjectType("java/lang/invoke/MethodType"));
	static final BasicValue STRING_VALUE = new NotNullValue(Type.getObjectType("java/lang/String"));
	static final BasicValue METHOD_HANDLE_VALUE = new NotNullValue(Type.getObjectType("java/lang/invoke/MethodHandle"));


	static boolean isInstance(Conf curr, Conf prev)
	{
		if(curr.insnIndex != prev.insnIndex)
		{
			return false;
		}
		Frame<BasicValue> currFr = curr.frame;
		Frame<BasicValue> prevFr = prev.frame;
		for(int i = 0; i < currFr.getLocals(); i++)
		{
			if(!isInstance(currFr.getLocal(i), prevFr.getLocal(i)))
			{
				return false;
			}
		}
		for(int i = 0; i < currFr.getStackSize(); i++)
		{
			if(!isInstance(currFr.getStack(i), prevFr.getStack(i)))
			{
				return false;
			}
		}
		return true;
	}

	static boolean isInstance(BasicValue curr, BasicValue prev)
	{
		if(prev instanceof ParamValue)
		{
			return curr instanceof ParamValue;
		}
		if(InstanceOfCheckValue == prev)
		{
			return InstanceOfCheckValue == curr;
		}
		if(TrueValue == prev)
		{
			return TrueValue == curr;
		}
		if(FalseValue == prev)
		{
			return FalseValue == curr;
		}
		if(NullValue == prev)
		{
			return NullValue == curr;
		}
		if(prev instanceof NotNullValue)
		{
			return curr instanceof NotNullValue;
		}
		if(prev instanceof CallResultValue)
		{
			if(curr instanceof CallResultValue)
			{
				CallResultValue prevCall = (CallResultValue) prev;
				CallResultValue currCall = (CallResultValue) curr;
				return prevCall.inters.equals(currCall.inters);
			}
			else
			{
				return false;
			}
		}
		return true;
	}

	static boolean equiv(Conf curr, Conf prev)
	{
		Frame<BasicValue> currFr = curr.frame;
		Frame<BasicValue> prevFr = prev.frame;
		for(int i = currFr.getStackSize() - 1; i >= 0; i--)
		{
			if(!equiv(currFr.getStack(i), prevFr.getStack(i)))
			{
				return false;
			}
		}
		for(int i = currFr.getLocals() - 1; i >= 0; i--)
		{
			if(!equiv(currFr.getLocal(i), prevFr.getLocal(i)))
			{
				return false;
			}
		}
		return true;
	}

	static boolean equiv(BasicValue curr, BasicValue prev)
	{
		if(curr.getClass() == prev.getClass())
		{
			if(curr instanceof CallResultValue && prev instanceof CallResultValue)
			{
				Set<EKey> keys1 = ((CallResultValue) prev).inters;
				Set<EKey> keys2 = ((CallResultValue) curr).inters;
				return keys1.equals(keys2);
			}
			else
			{
				return true;
			}
		}
		else
		{
			return false;
		}
	}
}