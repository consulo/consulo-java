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
package com.intellij.java.language.psi;

import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a Java <code>return</code> statement.
 */
public interface PsiReturnStatement extends PsiStatement
{
	public static final PsiReturnStatement[] EMPTY_ARRAY = new PsiReturnStatement[0];

	public static ArrayFactory<PsiReturnStatement> ARRAY_FACTORY = new ArrayFactory<PsiReturnStatement>()
	{
		@Nonnull
		@Override
		public PsiReturnStatement[] create(int count)
		{
			return count == 0 ? EMPTY_ARRAY : new PsiReturnStatement[count];
		}
	};

	/**
	 * Returns the expression representing the value returned by the statement.
	 *
	 * @return the return value expression, or null if the statement does not return any value.
	 */
	@Nullable
	PsiExpression getReturnValue();
}
