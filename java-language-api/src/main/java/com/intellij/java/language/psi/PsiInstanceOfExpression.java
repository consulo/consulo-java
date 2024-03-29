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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a Java <code>instanceof</code> expression.
 */
public interface PsiInstanceOfExpression extends PsiExpression
{
	/**
	 * Returns the expression on the left side of the <code>instanceof</code>.
	 *
	 * @return the checked expression.
	 */
	@Nonnull
	PsiExpression getOperand();

	/**
	 * Returns the type element on the right side of the <code>instanceof</code>.
	 *
	 * @return the type element, or null if the expression is incomplete.
	 */
	@Nullable
	PsiTypeElement getCheckType();


	/**
	 * @return pattern against which operand will be matched
	 */
	@Nullable
	PsiPattern getPattern();
}
