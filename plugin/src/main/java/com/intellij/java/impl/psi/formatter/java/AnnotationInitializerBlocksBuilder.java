/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import static com.intellij.java.impl.psi.formatter.java.JavaFormatterUtil.getWrapType;

import java.util.List;

import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Wrap;
import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiNameValuePair;
import consulo.language.psi.PsiWhiteSpace;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import consulo.language.psi.util.PsiTreeUtil;

public class AnnotationInitializerBlocksBuilder
{

	private final BlockFactory myFactory;
	private final ASTNode myNode;
	private final JavaCodeStyleSettings myJavaSettings;

	public AnnotationInitializerBlocksBuilder(ASTNode node, BlockFactory factory)
	{
		myNode = node;
		myFactory = factory;
		myJavaSettings = myFactory.getJavaSettings();
	}

	public List<Block> buildBlocks()
	{
		final Wrap wrap = Wrap.createWrap(getWrapType(myJavaSettings.ANNOTATION_PARAMETER_WRAP), false);
		final Alignment alignment = myJavaSettings.ALIGN_MULTILINE_ANNOTATION_PARAMETERS ? Alignment.createAlignment() : null;

		ChildrenBlocksBuilder.Config config = new ChildrenBlocksBuilder.Config().setDefaultIndent(Indent.getContinuationWithoutFirstIndent()).setIndent(JavaTokenType.RPARENTH, Indent.getNoneIndent()
		).setIndent(JavaTokenType.LPARENTH, Indent.getNoneIndent())

				.setDefaultWrap(wrap).setNoWrap(JavaTokenType.COMMA).setNoWrap(JavaTokenType.RPARENTH).setNoWrap(JavaTokenType.LPARENTH)

				.setDefaultAlignment(alignment).setNoAlignment(JavaTokenType.COMMA).setNoAlignment(JavaTokenType.LPARENTH).setNoAlignmentIf(JavaTokenType.RPARENTH, node -> {
					PsiElement prev = PsiTreeUtil.skipSiblingsBackward(node.getPsi(), PsiWhiteSpace.class);
					if(prev == null)
					{
						return false;
					}
					return prev instanceof PsiNameValuePair && !PsiTreeUtil.hasErrorElements(prev);
				});

		return config.createBuilder().buildNodeChildBlocks(myNode, myFactory);
	}
}
