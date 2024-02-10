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

import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.ChildAttributes;
import consulo.language.codeStyle.FormattingMode;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Wrap;
import consulo.language.codeStyle.WrapType;
import consulo.language.codeStyle.AlignmentStrategy;
import com.intellij.java.language.impl.JavaFileType;
import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import consulo.language.codeStyle.FormatterUtil;
import jakarta.annotation.Nonnull;

public class LabeledJavaBlock extends AbstractJavaBlock
{
	public LabeledJavaBlock(ASTNode node,
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
		Indent currentIndent = getLabelIndent();
		Wrap currentWrap = null;
		while(child != null)
		{
			if(!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0)
			{
				result.add(createJavaBlock(child, mySettings, myJavaSettings, currentIndent, currentWrap, AlignmentStrategy.getNullStrategy(), getFormattingMode()));
				if(child.getElementType() == JavaTokenType.COLON)
				{
					currentIndent = Indent.getNoneIndent();
					currentWrap = Wrap.createWrap(WrapType.ALWAYS, true);
				}
			}
			child = child.getTreeNext();
		}
		return result;
	}

	private Indent getLabelIndent()
	{
		if(mySettings.getRootSettings().getIndentOptions(JavaFileType.INSTANCE).LABEL_INDENT_ABSOLUTE)
		{
			return Indent.getAbsoluteLabelIndent();
		}
		else
		{
			return Indent.getLabelIndent();
		}
	}

	@Override
	@Nonnull
	public ChildAttributes getChildAttributes(final int newChildIndex)
	{
		return new ChildAttributes(Indent.getNoneIndent(), null);
	}
}
