/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * created at Feb 24, 2002
 *
 * @author Jeka
 */
package com.intellij.java.compiler.impl.classParsing;

import java.io.DataOutput;
import java.io.IOException;

public class ConstantValue
{
	public static final ConstantValue EMPTY_CONSTANT_VALUE = new ConstantValue()
	{
		@Override
		public int hashCode()
		{
			return 0;
		}

		@Override
		public boolean equals(Object obj)
		{
			return obj == this;
		}
	};

	protected ConstantValue()
	{
	}

	public void save(DataOutput out) throws IOException
	{
	}
}
