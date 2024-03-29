/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;
import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.FormattingMode;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Wrap;
import consulo.language.codeStyle.AlignmentStrategy;
import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import consulo.language.codeStyle.FormatterUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.ast.IElementType;

public class ExtendsListBlock extends AbstractJavaBlock
{
	public ExtendsListBlock(ASTNode node,
							Wrap wrap,
							Alignment alignment,
							CommonCodeStyleSettings settings,
							JavaCodeStyleSettings javaSettings,
							@Nonnull FormattingMode formattingMode)
	{
		super(node, wrap, alignment, Indent.getNoneIndent(), settings, javaSettings, formattingMode);
	}

	public ExtendsListBlock(ASTNode node,
							Wrap wrap,
							AlignmentStrategy alignmentStrategy,
							CommonCodeStyleSettings settings,
							JavaCodeStyleSettings javaSettings,
							@Nonnull FormattingMode formattingMode)
	{
		super(node, wrap, alignmentStrategy, Indent.getNoneIndent(), settings, javaSettings, formattingMode);
	}

	@Override
	protected List<Block> buildChildren()
	{
		final ArrayList<Block> result = new ArrayList<>();
		ArrayList<Block> elementsExceptKeyword = new ArrayList<>();
		myChildAlignment = createChildAlignment();
		myChildIndent = Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS);
		myUseChildAttributes = true;
		Wrap childWrap = createChildWrap();
		ASTNode child = myNode.getFirstChildNode();

		Alignment alignment = alignList() ? Alignment.createAlignment() : null;

		while(child != null)
		{
			if(!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0)
			{
				IElementType elementType = child.getElementType();
				if(ElementType.KEYWORD_BIT_SET.contains(elementType))
				{
					if(!elementsExceptKeyword.isEmpty())
					{
						result.add(new SyntheticCodeBlock(elementsExceptKeyword, null, mySettings, myJavaSettings, Indent.getNoneIndent(), null));
						elementsExceptKeyword = new ArrayList<>();
					}
					Indent indent = mySettings.ALIGN_THROWS_KEYWORD
							&& elementType == JavaTokenType.THROWS_KEYWORD ? Indent.getNoneIndent() : myChildIndent;

					result.add(createJavaBlock(child, mySettings, myJavaSettings, indent, arrangeChildWrap(child, childWrap), alignment, getFormattingMode()));
				}
				else
				{
					Alignment candidate = myAlignmentStrategy.getAlignment(elementType);
					if(candidate != null)
					{
						alignment = myChildAlignment = candidate;
					}
					processChild(elementsExceptKeyword, child, myChildAlignment, childWrap, myChildIndent);
				}
			}
			child = child.getTreeNext();
		}
		if(!elementsExceptKeyword.isEmpty())
		{
			result.add(new SyntheticCodeBlock(elementsExceptKeyword, alignment, mySettings, myJavaSettings, Indent.getNoneIndent(), null));
		}

		return result;

	}

	private boolean alignList()
	{
		if(myNode.getElementType() == JavaElementType.EXTENDS_LIST || myNode.getElementType() == JavaElementType.IMPLEMENTS_LIST)
		{
			return mySettings.ALIGN_MULTILINE_EXTENDS_LIST;
		}
		else if(myNode.getElementType() == JavaElementType.THROWS_LIST)
		{
			return mySettings.ALIGN_MULTILINE_THROWS_LIST;
		}
		return false;
	}
}
