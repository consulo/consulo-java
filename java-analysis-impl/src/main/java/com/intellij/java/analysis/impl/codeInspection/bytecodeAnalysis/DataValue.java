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

import com.intellij.java.analysis.impl.codeInspection.dataFlow.ContractReturnValue;

import java.util.stream.Stream;

// data for data analysis
abstract class DataValue implements consulo.internal.org.objectweb.asm.tree.analysis.Value
{
	public static final DataValue[] EMPTY = new DataValue[0];

	private final int myHash;

	DataValue(int hash)
	{
		myHash = hash;
	}

	@Override
	public final int hashCode()
	{
		return myHash;
	}

	Stream<EKey> dependencies()
	{
		return Stream.empty();
	}

	public ContractReturnValue asContractReturnValue()
	{
		return ContractReturnValue.returnAny();
	}

	static final DataValue ThisDataValue = new DataValue(-1)
	{
		@Override
		public int getSize()
		{
			return 1;
		}

		@Override
		public ContractReturnValue asContractReturnValue()
		{
			return ContractReturnValue.returnThis();
		}

		@Override
		public String toString()
		{
			return "DataValue: this";
		}
	};
	static final DataValue LocalDataValue = new DataValue(-2)
	{
		@Override
		public int getSize()
		{
			return 1;
		}

		@Override
		public ContractReturnValue asContractReturnValue()
		{
			return ContractReturnValue.returnNew();
		}

		@Override
		public String toString()
		{
			return "DataValue: local";
		}
	};

	static class ParameterDataValue extends DataValue
	{
		static final ParameterDataValue PARAM0 = new ParameterDataValue(0);
		static final ParameterDataValue PARAM1 = new ParameterDataValue(1);
		static final ParameterDataValue PARAM2 = new ParameterDataValue(2);

		final int n;

		private ParameterDataValue(int n)
		{
			super(n);
			this.n = n;
		}

		@Override
		public ContractReturnValue asContractReturnValue()
		{
			return ContractReturnValue.returnParameter(n);
		}

		static ParameterDataValue create(int n)
		{
			switch(n)
			{
				case 0:
					return PARAM0;
				case 1:
					return PARAM1;
				case 2:
					return PARAM2;
				default:
					return new ParameterDataValue(n);
			}
		}

		@Override
		public int getSize()
		{
			return 1;
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
			ParameterDataValue that = (ParameterDataValue) o;
			return n == that.n;
		}

		@Override
		public String toString()
		{
			return "DataValue: arg#" + n;
		}
	}

	static class ReturnDataValue extends DataValue
	{
		final EKey key;

		ReturnDataValue(EKey key)
		{
			super(key.hashCode());
			this.key = key;
		}

		@Override
		public int getSize()
		{
			return 1;
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
			ReturnDataValue that = (ReturnDataValue) o;
			return key.equals(that.key);
		}

		@Override
		Stream<EKey> dependencies()
		{
			return Stream.of(key);
		}

		@Override
		public String toString()
		{
			return "Return of: " + key;
		}
	}

	static final DataValue OwnedDataValue = new DataValue(-3)
	{
		@Override
		public int getSize()
		{
			return 1;
		}

		@Override
		public String toString()
		{
			return "DataValue: owned";
		}
	};
	static final DataValue UnknownDataValue1 = new DataValue(-4)
	{
		@Override
		public int getSize()
		{
			return 1;
		}

		@Override
		public String toString()
		{
			return "DataValue: unknown (1-slot)";
		}
	};
	static final DataValue UnknownDataValue2 = new DataValue(-5)
	{
		@Override
		public int getSize()
		{
			return 2;
		}

		@Override
		public String toString()
		{
			return "DataValue: unknown (2-slot)";
		}
	};
}