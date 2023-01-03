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
package com.intellij.java.impl.psi.formatter.java;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.codeStyle.AlignmentStrategy;
import consulo.language.ast.ASTNode;
import consulo.util.lang.StringUtil;
import consulo.language.ast.TokenType;

public abstract class ChildAlignmentStrategyProvider
{

	public abstract AlignmentStrategy getNextChildStrategy(@Nonnull ASTNode child);

	public static ChildAlignmentStrategyProvider NULL_STRATEGY_PROVIDER = new ChildAlignmentStrategyProvider()
	{
		@Override
		public AlignmentStrategy getNextChildStrategy(@Nonnull ASTNode child)
		{
			return AlignmentStrategy.getNullStrategy();
		}
	};

	public static boolean isWhiteSpaceWithBlankLines(@Nullable ASTNode node)
	{
		return node != null && node.getElementType() == TokenType.WHITE_SPACE && StringUtil.countNewLines(node.getChars()) > 1;
	}
}
