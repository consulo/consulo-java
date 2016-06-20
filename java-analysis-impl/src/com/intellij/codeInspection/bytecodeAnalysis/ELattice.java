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

/**
 * For lattice, equations and solver description, see http://pat.keldysh.ru/~ilya/faba.pdf (in Russian)
 */
final class ELattice<T extends Enum<T>>
{
	final T bot;
	final T top;

	ELattice(T bot, T top)
	{
		this.bot = bot;
		this.top = top;
	}

	final T join(T x, T y)
	{
		if(x == bot)
		{
			return y;
		}
		if(y == bot)
		{
			return x;
		}
		if(x == y)
		{
			return x;
		}
		return top;
	}

	final T meet(T x, T y)
	{
		if(x == top)
		{
			return y;
		}
		if(y == top)
		{
			return x;
		}
		if(x == y)
		{
			return x;
		}
		return bot;
	}
}
