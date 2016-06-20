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

import java.util.Set;

import org.jetbrains.annotations.NotNull;

final class Product
{
	@NotNull
	final Value value;
	@NotNull
	final Set<Key> ids;

	Product(@NotNull Value value, @NotNull Set<Key> ids)
	{
		this.value = value;
		this.ids = ids;
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

		Product product = (Product) o;

		if(!ids.equals(product.ids))
		{
			return false;
		}
		if(!value.equals(product.value))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = value.hashCode();
		result = 31 * result + ids.hashCode();
		return result;
	}
}
