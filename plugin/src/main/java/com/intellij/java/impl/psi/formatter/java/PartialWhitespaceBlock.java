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
package com.intellij.java.impl.psi.formatter.java;

import javax.annotation.Nonnull;

import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.FormattingMode;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Wrap;
import consulo.language.codeStyle.AlignmentStrategy;
import consulo.language.ast.ASTNode;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;

/**
 * @author max
 */
public class PartialWhitespaceBlock extends SimpleJavaBlock
{
	private final TextRange myRange;

	public PartialWhitespaceBlock(ASTNode node,
								  TextRange range,
								  Wrap wrap,
								  Alignment alignment,
								  Indent indent,
								  CommonCodeStyleSettings settings,
								  JavaCodeStyleSettings javaSettings,
								  @Nonnull FormattingMode formattingMode)
	{
		super(node, wrap, AlignmentStrategy.wrap(alignment), indent, settings, javaSettings, formattingMode);
		myRange = range;
	}

	@Nonnull
	@Override
	public TextRange getTextRange()
	{
		return myRange;
	}
}
