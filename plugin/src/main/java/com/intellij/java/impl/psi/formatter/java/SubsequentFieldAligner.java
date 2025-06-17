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

import com.intellij.java.impl.psi.impl.source.tree.JavaJspElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.codeStyle.AlignmentInColumnsConfig;
import consulo.language.codeStyle.AlignmentInColumnsHelper;
import consulo.language.codeStyle.AlignmentStrategy;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import jakarta.annotation.Nonnull;

import java.util.Set;

public class SubsequentFieldAligner extends ChildAlignmentStrategyProvider
{

	private final static Set<IElementType> TYPES_TO_ALIGN = Set.of(JavaElementType.MODIFIER_LIST, JavaElementType.TYPE, JavaTokenType.IDENTIFIER, JavaTokenType.EQ);

	private static final AlignmentInColumnsConfig ALIGNMENT_IN_COLUMNS_CONFIG = new AlignmentInColumnsConfig(TokenSet.create(JavaTokenType.IDENTIFIER), JavaJspElementType.WHITE_SPACE_BIT_SET,
			ElementType.JAVA_COMMENT_BIT_SET, TokenSet.create(JavaTokenType.EQ), TokenSet.create(JavaElementType.FIELD));

	private final CommonCodeStyleSettings mySettings;
	private final AlignmentInColumnsHelper myAlignmentInColumnsHelper;
	private AlignmentStrategy myAlignmentStrategy;

	public SubsequentFieldAligner(@Nonnull CommonCodeStyleSettings settings)
	{
		myAlignmentInColumnsHelper = AlignmentInColumnsHelper.INSTANCE;
		myAlignmentStrategy = newAlignmentStrategy();
		mySettings = settings;
	}

	private AlignmentStrategy newAlignmentStrategy()
	{
		return AlignmentStrategy.createAlignmentPerTypeStrategy(TYPES_TO_ALIGN, JavaElementType.FIELD, true);
	}

	@Override
	public AlignmentStrategy getNextChildStrategy(@Nonnull ASTNode child)
	{
		if(!ElementType.JAVA_COMMENT_BIT_SET.contains(child.getElementType()) && !shouldUseVarDeclarationAlignment(child))
		{
			myAlignmentStrategy = newAlignmentStrategy();
		}

		return JavaElementType.FIELD == child.getElementType() ? myAlignmentStrategy : AlignmentStrategy.getNullStrategy();
	}

	protected boolean shouldUseVarDeclarationAlignment(@Nonnull ASTNode node)
	{
		return JavaElementType.FIELD == node.getElementType() && (!myAlignmentInColumnsHelper.useDifferentVarDeclarationAlignment(node, ALIGNMENT_IN_COLUMNS_CONFIG, mySettings
				.KEEP_BLANK_LINES_IN_DECLARATIONS) || MultipleFieldDeclarationHelper.compoundFieldPart(node));
	}

}