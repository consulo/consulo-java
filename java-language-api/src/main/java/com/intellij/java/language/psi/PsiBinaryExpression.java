/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import consulo.language.ast.TokenSet;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a Java binary expression (addition, multiplication and so on).
 * N.B Please use {@link PsiPolyadicExpression} instead as a more general form of an infix operator-expression.
 */
public interface PsiBinaryExpression extends PsiPolyadicExpression
{
	TokenSet BOOLEAN_OPERATION_TOKENS = TokenSet.create(JavaTokenType.EQEQ, JavaTokenType.NE, JavaTokenType.LT, JavaTokenType.GT, JavaTokenType.LE, JavaTokenType.GE, JavaTokenType.OROR,
			JavaTokenType.ANDAND);

	/**
	 * Returns the left operand of the expression.
	 *
	 * @return the left operand.
	 */
	@Nonnull
	PsiExpression getLOperand();

	/**
	 * Returns the right operand of the expression.
	 *
	 * @return the right operand, or null if the expression is incomplete.
	 */
	@Nullable
	PsiExpression getROperand();

	/**
	 * Returns the token representing the operation (for example, {@link JavaTokenType#PLUS} for an
	 * addition operation).
	 *
	 * @return the operation token.
	 */
	@Nonnull
	PsiJavaToken getOperationSign();

	/**
	 * Returns the type of the token representing the operation performed.
	 *
	 * @return the token type.
	 */
	@Override
	@Nonnull
	IElementType getOperationTokenType();
}
