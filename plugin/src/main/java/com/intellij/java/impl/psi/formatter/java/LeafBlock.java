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
package com.intellij.java.impl.psi.formatter.java;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.codeStyle.ASTBlock;
import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.ChildAttributes;
import consulo.language.codeStyle.FormattingRangesInfo;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Spacing;
import consulo.language.codeStyle.Wrap;
import consulo.language.ast.ASTNode;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.ExtraRangesProvider;
import consulo.language.codeStyle.NodeIndentRangesCalculator;
import com.intellij.java.impl.psi.impl.source.codeStyle.ShiftIndentInsideHelper;

public class LeafBlock implements ASTBlock, ExtraRangesProvider
{
	private int myStartOffset = -1;
	private final ASTNode myNode;
	private final Wrap myWrap;
	private final Alignment myAlignment;

	private static final ArrayList<Block> EMPTY_SUB_BLOCKS = new ArrayList<>();
	private final Indent myIndent;

	public LeafBlock(final ASTNode node,
					 final Wrap wrap,
					 final Alignment alignment,
					 Indent indent)
	{
		myNode = node;
		myWrap = wrap;
		myAlignment = alignment;
		myIndent = indent;
	}

	@Override
	public ASTNode getNode()
	{
		return myNode;
	}

	@Override
	@Nonnull
	public TextRange getTextRange()
	{
		if(myStartOffset != -1)
		{
			return new TextRange(myStartOffset, myStartOffset + myNode.getTextLength());
		}
		return myNode.getTextRange();
	}

	@Override
	@Nonnull
	public List<Block> getSubBlocks()
	{
		return EMPTY_SUB_BLOCKS;
	}

	@Override
	public Wrap getWrap()
	{
		return myWrap;
	}

	@Override
	public Indent getIndent()
	{
		return myIndent;
	}

	@Override
	public Alignment getAlignment()
	{
		return myAlignment;
	}

	@Override
	public Spacing getSpacing(Block child1, @Nonnull Block child2)
	{
		return null;
	}

	public ASTNode getTreeNode()
	{
		return myNode;
	}

	@Override
	@Nonnull
	public ChildAttributes getChildAttributes(final int newChildIndex)
	{
		return new ChildAttributes(getIndent(), null);
	}

	@Override
	public boolean isIncomplete()
	{
		return false;
	}

	@Override
	public boolean isLeaf()
	{
		return ShiftIndentInsideHelper.mayShiftIndentInside(myNode);
	}

	public void setStartOffset(final int startOffset)
	{
		myStartOffset = startOffset;
		// if (startOffset != -1) assert startOffset == myNode.getTextRange().getStartOffset();
	}

	@Override
	@Nullable
	public List<TextRange> getExtraRangesToFormat(@Nonnull FormattingRangesInfo info)
	{
		int startOffset = getTextRange().getStartOffset();
		if(info.isOnInsertedLine(startOffset) && myNode.getTextLength() == 1 && myNode.textContains('}'))
		{
			ASTNode parent = myNode.getTreeParent();
			return new NodeIndentRangesCalculator(parent).calculateExtraRanges();
		}
		return null;
	}
}
