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

import javax.annotation.Nonnull;

final class Equation
{
	@Nonnull
	final EKey key;
	@Nonnull
	final Result result;

	Equation(@Nonnull EKey key, @Nonnull Result result)
	{
		this.key = key;
		this.result = result;
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
		Equation equation = (Equation) o;
		return key.equals(equation.key) && result.equals(equation.result);
	}

	@Override
	public int hashCode()
	{
		return 31 * key.hashCode() + result.hashCode();
	}

	@Override
	public String toString()
	{
		return "Equation{" + "key=" + key + ", result=" + result + '}';
	}
}
