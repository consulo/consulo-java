/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import consulo.logging.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;

public class SyntheticCodeBlock implements Block, JavaBlock
{
	private final List<Block> mySubBlocks;
	private final Alignment myAlignment;
	private final Indent myIndentContent;
	private final CommonCodeStyleSettings mySettings;
	private final JavaCodeStyleSettings myJavaSettings;
	private final Wrap myWrap;

	private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.newXmlFormatter.java.SyntheticCodeBlock");

	private final TextRange myTextRange;

	private ChildAttributes myChildAttributes;
	private boolean myIsIncomplete;

	public SyntheticCodeBlock(List<Block> subBlocks,
							  Alignment alignment,
							  CommonCodeStyleSettings settings,
							  JavaCodeStyleSettings javaSettings,
							  Indent indent,
							  Wrap wrap)
	{
		myJavaSettings = javaSettings;
		myIndentContent = indent;
		if(subBlocks.isEmpty())
		{
			LOG.assertTrue(false);
		}
		mySubBlocks = new ArrayList<>(subBlocks);
		myAlignment = alignment;
		mySettings = settings;
		myWrap = wrap;
		myTextRange = new TextRange(mySubBlocks.get(0).getTextRange().getStartOffset(),
				mySubBlocks.get(mySubBlocks.size() - 1).getTextRange().getEndOffset());
	}

	@Override
	@Nonnull
	public TextRange getTextRange()
	{
		return myTextRange;
	}

	@Override
	@Nonnull
	public List<Block> getSubBlocks()
	{
		return mySubBlocks;
	}

	@Override
	public Wrap getWrap()
	{
		return myWrap;
	}

	@Override
	public Indent getIndent()
	{
		return myIndentContent;
	}

	@Override
	public Alignment getAlignment()
	{
		return myAlignment;
	}

	@Override
	public Spacing getSpacing(Block child1, @Nonnull Block child2)
	{
		return JavaSpacePropertyProcessor.getSpacing(child2, mySettings, myJavaSettings);
	}

	public String toString()
	{
		ASTNode treeNode = null;
		Block child = mySubBlocks.get(0);
		while(treeNode == null)
		{
			if(child instanceof AbstractBlock)
			{
				treeNode = ((AbstractBlock) child).getNode();
			}
			else if(child instanceof SyntheticCodeBlock)
			{
				child = ((SyntheticCodeBlock) child).mySubBlocks.get(0);
			}
			else
			{
				break;
			}
		}
		final TextRange textRange = getTextRange();
		if(treeNode != null)
		{
			PsiElement psi = treeNode.getPsi();
			if(psi != null)
			{
				PsiFile file = psi.getContainingFile();
				if(file != null)
				{
					return file.getText().subSequence(textRange.getStartOffset(), textRange.getEndOffset()) + " " + textRange;
				}
			}
		}
		return getClass().getName() + ": " + textRange;
	}

	@Override
	public ASTNode getFirstTreeNode()
	{
		ASTNode result = AbstractJavaBlock.getTreeNode(mySubBlocks.get(0));
		assert result != null;
		return result;
	}

	public void setChildAttributes(final ChildAttributes childAttributes)
	{
		myChildAttributes = childAttributes;
	}

	@Override
	@Nonnull
	public ChildAttributes getChildAttributes(final int newChildIndex)
	{
		if(myChildAttributes != null)
		{
			return myChildAttributes;
		}
		else
		{
			Alignment alignment = null;
			if(mySubBlocks.size() > newChildIndex)
			{
				Block block = mySubBlocks.get(newChildIndex);
				alignment = block.getAlignment();
			}
			else if(mySubBlocks.size() == newChildIndex)
			{
				if(isRParenth(getRightMostBlock()))
				{
					alignment = getDotAlignment();
				}
			}

			return new ChildAttributes(getIndent(), alignment);
		}
	}

	private static boolean isRParenth(Block sibling)
	{
		if(sibling instanceof LeafBlock)
		{
			ASTNode node = ((LeafBlock) sibling).getNode();
			return node != null && node.getElementType() == JavaTokenType.RPARENTH;
		}
		return false;
	}

	@Nullable
	private Alignment getDotAlignment()
	{
		if(mySubBlocks.size() > 1)
		{
			Block block = mySubBlocks.get(1);
			if(isDotFirst(block))
			{
				return block.getAlignment();
			}
		}
		return null;
	}

	private static boolean isDotFirst(final Block block)
	{
		Block current = block;
		while(!current.getSubBlocks().isEmpty())
		{
			current = block.getSubBlocks().get(0);
		}
		ASTNode node = current instanceof LeafBlock ? ((LeafBlock) current).getNode() : null;
		return node != null && node.getElementType() == JavaTokenType.DOT;
	}

	@Nullable
	private Block getRightMostBlock()
	{
		Block rightMost = null;
		List<Block> subBlocks = getSubBlocks();
		while(!subBlocks.isEmpty())
		{
			int lastIndex = subBlocks.size() - 1;
			rightMost = subBlocks.get(lastIndex);
			subBlocks = rightMost.getSubBlocks();
		}
		return rightMost;
	}

	@Override
	public boolean isIncomplete()
	{
		if(myIsIncomplete)
		{
			return true;
		}
		return getSubBlocks().get(getSubBlocks().size() - 1).isIncomplete();
	}

	@Override
	public boolean isLeaf()
	{
		return false;
	}

	public void setIsIncomplete(final boolean isIncomplete)
	{
		myIsIncomplete = isIncomplete;
	}
}
