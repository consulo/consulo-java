/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import consulo.language.ast.IElementType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a Java unary operation (either prefix or postfix)
 */
public interface PsiUnaryExpression extends PsiExpression
{
	/**
	 * Returns the expression representing the operand of the unary operator.
	 *
	 * @return the operand expression, or null if the expression is incomplete.
	 */
	@Nullable
	PsiExpression getOperand();

	/**
	 * Returns the token representing the operation performed.
	 *
	 * @return the token for the operation performed.
	 */
	@Nonnull
	PsiJavaToken getOperationSign();

	/**
	 * Returns the type of the token representing the operation performed.
	 *
	 * @return the token type.
	 */
	@Nonnull
	IElementType getOperationTokenType();
}
