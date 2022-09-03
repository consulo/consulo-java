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

/*
 * @author max
 */
package com.intellij.java.impl.psi.impl.source.codeStyle;

import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.codeStyle.Block;
import com.intellij.java.language.impl.JavaFileType;
import consulo.language.ast.ASTNode;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.ast.TokenType;
import consulo.language.codeStyle.DocumentBasedFormattingModel;
import consulo.language.codeStyle.FormatterUtil;
import consulo.ide.impl.psi.formatter.FormattingDocumentModelImpl;
import consulo.language.codeStyle.PsiBasedFormattingModel;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.ast.IElementType;
import com.intellij.psi.xml.XmlTokenType;

public class PsiBasedFormatterModelWithShiftIndentInside extends PsiBasedFormattingModel
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle" +
			".PsiBasedFormatterModelWithShiftIndentInside");

	private final Project myProject;

	public PsiBasedFormatterModelWithShiftIndentInside(final PsiFile file,
			@Nonnull final Block rootBlock,
			final FormattingDocumentModelImpl documentModel)
	{
		super(file, rootBlock, documentModel);
		myProject = file.getProject();
	}

	@Override
	public TextRange shiftIndentInsideRange(ASTNode node, TextRange textRange, int shift)
	{
		return shiftIndentInsideWithPsi(node, textRange, shift);
	}

	@RequiredReadAction
	private TextRange shiftIndentInsideWithPsi(ASTNode node, final TextRange textRange, final int shift)
	{
		if(node != null && node.getTextRange().equals(textRange) && ShiftIndentInsideHelper.mayShiftIndentInside(node))
		{
			return new ShiftIndentInsideHelper(JavaFileType.INSTANCE, myProject).shiftIndentInside(node,
					shift).getTextRange();
		}
		else
		{
			return textRange;
		}

	}

	@Override
	protected String replaceWithPsiInLeaf(final TextRange textRange, String whiteSpace, ASTNode leafElement)
	{
		if(!myCanModifyAllWhiteSpaces)
		{
			if(leafElement.getElementType() == TokenType.WHITE_SPACE)
			{
				return null;
			}
			ASTNode prevNode = TreeUtil.prevLeaf(leafElement);

			if(prevNode != null)
			{
				IElementType type = prevNode.getElementType();
				if(type == TokenType.WHITE_SPACE)
				{
					final String text = prevNode.getText();

					@NonNls final String cdataStartMarker = "<![CDATA[";
					final int cdataPos = text.indexOf(cdataStartMarker);
					if(cdataPos != -1 && whiteSpace.indexOf(cdataStartMarker) == -1)
					{
						whiteSpace = DocumentBasedFormattingModel.mergeWsWithCdataMarker(whiteSpace, text, cdataPos);
						if(whiteSpace == null)
						{
							return null;
						}
					}

					prevNode = TreeUtil.prevLeaf(prevNode);
					type = prevNode != null ? prevNode.getElementType() : null;
				}

				@NonNls final String cdataEndMarker = "]]>";
				if(type == XmlTokenType.XML_CDATA_END && whiteSpace.indexOf(cdataEndMarker) == -1)
				{
					final ASTNode at = findElementAt(prevNode.getStartOffset());

					if(at != null && at.getPsi() instanceof PsiWhiteSpace)
					{
						final String s = at.getText();
						final int cdataEndPos = s.indexOf(cdataEndMarker);
						whiteSpace = DocumentBasedFormattingModel.mergeWsWithCdataMarker(whiteSpace, s, cdataEndPos);
						leafElement = at;
					}
					else
					{
						whiteSpace = null;
					}
					if(whiteSpace == null)
					{
						return null;
					}
				}
			}
		}
		FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, TokenType.WHITE_SPACE, textRange);
		return whiteSpace;
	}
}
