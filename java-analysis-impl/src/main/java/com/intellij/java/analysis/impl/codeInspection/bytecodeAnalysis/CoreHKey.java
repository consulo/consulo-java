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

import jakarta.annotation.Nonnull;

final class CoreHKey
{
	final
	@Nonnull
	MemberDescriptor myMethod;
	final int dirKey;

	CoreHKey(@Nonnull MemberDescriptor method, int dirKey)
	{
		this.myMethod = method;
		this.dirKey = dirKey;
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

		CoreHKey coreHKey = (CoreHKey) o;
		return dirKey == coreHKey.dirKey && myMethod.equals(coreHKey.myMethod);
	}

	@Override
	public int hashCode()
	{
		return 31 * myMethod.hashCode() + dirKey;
	}

	@Override
	public String toString()
	{
		return "CoreHKey [" + myMethod + "|" + Direction.fromInt(dirKey) + "]";
	}
}
