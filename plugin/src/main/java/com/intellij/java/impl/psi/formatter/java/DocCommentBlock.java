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

import javax.annotation.Nonnull;

import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.ChildAttributes;
import consulo.language.codeStyle.FormattingMode;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Wrap;
import consulo.language.codeStyle.AlignmentStrategy;
import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaDocTokenType;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import consulo.language.codeStyle.FormatterUtil;

public class DocCommentBlock extends AbstractJavaBlock
{
	public DocCommentBlock(ASTNode node,
						   Wrap wrap,
						   Alignment alignment,
						   Indent indent,
						   CommonCodeStyleSettings settings,
						   JavaCodeStyleSettings javaSettings,
						   @Nonnull FormattingMode formattingMode)
	{
		super(node, wrap, alignment, indent, settings, javaSettings, formattingMode);
	}

	@Override
	protected List<Block> buildChildren()
	{
		final ArrayList<Block> result = new ArrayList<>();

		ASTNode child = myNode.getFirstChildNode();
		while(child != null)
		{
			if(child.getElementType() == JavaDocTokenType.DOC_COMMENT_START)
			{
				result.add(createJavaBlock(child, mySettings, myJavaSettings, Indent.getNoneIndent(), null, AlignmentStrategy.getNullStrategy(), getFormattingMode()));
			}
			else if(!FormatterUtil.containsWhiteSpacesOnly(child) && !child.getText().trim().isEmpty())
			{
				result.add(createJavaBlock(child, mySettings, myJavaSettings, Indent.getSpaceIndent(1), null, AlignmentStrategy.getNullStrategy(), getFormattingMode()));
			}
			child = child.getTreeNext();
		}
		return result;

	}

	@Override
	@Nonnull
	public ChildAttributes getChildAttributes(final int newChildIndex)
	{
		return new ChildAttributes(Indent.getSpaceIndent(1), null);
	}
}
