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

package com.intellij.psi.formatter.java;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.openapi.util.TextRange;

/**
 * @author max
 */
public class ReadonlyWhitespaceBlock implements Block
{
	private final TextRange myRange;
	private final Wrap myWrap;
	private final Alignment myAlignment;
	private final Indent myIndent;

	public ReadonlyWhitespaceBlock(final TextRange range, final Wrap wrap, final Alignment alignment, final Indent indent)
	{
		myRange = range;
		myWrap = wrap;
		myAlignment = alignment;
		myIndent = indent;
	}

	@Override
	@Nonnull
	public TextRange getTextRange()
	{
		return myRange;
	}

	@Override
	@Nonnull
	public List<Block> getSubBlocks()
	{
		return Collections.emptyList();
	}

	@Override
	@javax.annotation.Nullable
	public Wrap getWrap()
	{
		return myWrap;
	}

	@Override
	@javax.annotation.Nullable
	public Indent getIndent()
	{
		return myIndent;
	}

	@Override
	@javax.annotation.Nullable
	public Alignment getAlignment()
	{
		return myAlignment;
	}

	@Override
	@Nullable
	public Spacing getSpacing(Block child1, @Nonnull Block child2)
	{
		return null;
	}

	@Override
	@Nonnull
	public ChildAttributes getChildAttributes(final int newChildIndex)
	{
		return ChildAttributes.DELEGATE_TO_NEXT_CHILD;
	}

	@Override
	public boolean isIncomplete()
	{
		return false;
	}

	@Override
	public boolean isLeaf()
	{
		return true;
	}
}
