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
package com.intellij.java.impl.codeInsight.editorActions;

import static consulo.language.codeStyle.Indent.Type.CONTINUATION;
import static consulo.ide.impl.psi.impl.source.codeStyle.lineIndent.JavaLikeLangLineIndentProvider.JavaLikeElement.*;

import java.util.HashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.codeStyle.Indent;
import consulo.language.Language;
import com.intellij.java.language.JavaLanguage;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.TokenType;
import consulo.ide.impl.psi.impl.source.codeStyle.SemanticEditorPosition;
import consulo.ide.impl.psi.impl.source.codeStyle.lineIndent.JavaLikeLangLineIndentProvider;
import consulo.language.ast.IElementType;

/**
 * @author Rustam Vishnyakov
 */
public class JavaLineIndentProvider extends JavaLikeLangLineIndentProvider
{
	private final static HashMap<IElementType, SemanticEditorPosition.SyntaxElement> SYNTAX_MAP = new HashMap<>();

	static
	{
		SYNTAX_MAP.put(TokenType.WHITE_SPACE, Whitespace);
		SYNTAX_MAP.put(JavaTokenType.SEMICOLON, Semicolon);
		SYNTAX_MAP.put(JavaTokenType.LBRACE, BlockOpeningBrace);
		SYNTAX_MAP.put(JavaTokenType.RBRACE, BlockClosingBrace);
		SYNTAX_MAP.put(JavaTokenType.LBRACKET, ArrayOpeningBracket);
		SYNTAX_MAP.put(JavaTokenType.RBRACKET, ArrayClosingBracket);
		SYNTAX_MAP.put(JavaTokenType.RPARENTH, RightParenthesis);
		SYNTAX_MAP.put(JavaTokenType.LPARENTH, LeftParenthesis);
		SYNTAX_MAP.put(JavaTokenType.COLON, Colon);
		SYNTAX_MAP.put(JavaTokenType.CASE_KEYWORD, SwitchCase);
		SYNTAX_MAP.put(JavaTokenType.DEFAULT_KEYWORD, SwitchDefault);
		SYNTAX_MAP.put(JavaTokenType.IF_KEYWORD, IfKeyword);
		SYNTAX_MAP.put(JavaTokenType.WHILE_KEYWORD, IfKeyword);
		SYNTAX_MAP.put(JavaTokenType.ELSE_KEYWORD, ElseKeyword);
		SYNTAX_MAP.put(JavaTokenType.FOR_KEYWORD, ForKeyword);
		SYNTAX_MAP.put(JavaTokenType.DO_KEYWORD, DoKeyword);
		SYNTAX_MAP.put(JavaTokenType.C_STYLE_COMMENT, BlockComment);
		SYNTAX_MAP.put(JavaDocTokenType.DOC_COMMENT_START, DocBlockStart);
		SYNTAX_MAP.put(JavaDocTokenType.DOC_COMMENT_END, DocBlockEnd);
		SYNTAX_MAP.put(JavaTokenType.COMMA, Comma);
		SYNTAX_MAP.put(JavaTokenType.END_OF_LINE_COMMENT, LineComment);
		SYNTAX_MAP.put(JavaTokenType.TRY_KEYWORD, TryKeyword);
	}

	@Nullable
	@Override
	protected SemanticEditorPosition.SyntaxElement mapType(@Nonnull IElementType tokenType)
	{
		return SYNTAX_MAP.get(tokenType);
	}

	@Override
	public boolean isSuitableForLanguage(@Nonnull Language language)
	{
		return language.isKindOf(JavaLanguage.INSTANCE);
	}

	@Nullable
	@Override
	protected Indent getIndentInBlock(@Nonnull Project project,
									  @Nullable Language language,
									  @Nonnull SemanticEditorPosition blockStartPosition)
	{
		SemanticEditorPosition beforeStart = blockStartPosition.before().beforeOptional(Whitespace);
		if(beforeStart.isAt(JavaTokenType.EQ) ||
				beforeStart.isAt(JavaTokenType.RBRACKET) ||
				beforeStart.isAt(JavaTokenType.LPARENTH)
				)
		{
			// For arrays like int x = {<caret>0, 1, 2}
			return getDefaultIndentFromType(CONTINUATION);
		}
		return super.getIndentInBlock(project, language, blockStartPosition);
	}

	@Override
	protected boolean isInsideForLikeConstruction(SemanticEditorPosition position)
	{
		return position.isAfterOnSameLine(ForKeyword, TryKeyword);
	}

	@Override
	protected boolean isInArray(@Nonnull Editor editor, int offset)
	{
		SemanticEditorPosition position = getPosition(editor, offset);
		position.moveBefore();
		if(position.isAt(JavaTokenType.LBRACE))
		{
			if(position.before().beforeOptional(Whitespace).isAt(JavaTokenType.RBRACKET))
				return true;
		}
		return super.isInArray(editor, offset);
	}

	@Override
	protected boolean isIndentProvider(@Nonnull SemanticEditorPosition position, boolean ignoreLabels)
	{
		return !(position.afterOptionalMix(Whitespace, BlockComment).after().isAt(Colon)
				&& position.isAt(JavaTokenType.IDENTIFIER));
	}
}
