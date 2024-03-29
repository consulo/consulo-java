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
package com.intellij.java.impl.psi.impl.cache.impl.idCache;

import consulo.language.lexer.Lexer;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.TokenType;
import consulo.language.psi.stub.BaseFilterLexer;
import consulo.language.psi.stub.OccurrenceConsumer;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import consulo.language.psi.search.UsageSearchContext;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;

/**
 * @author ven
 */
public class JavaFilterLexer extends BaseFilterLexer
{
	private static final TokenSet ourSkipWordsScanSet = TokenSet.orSet(TokenSet.create(TokenType.WHITE_SPACE, JavaTokenType.LPARENTH, JavaTokenType.RPARENTH, JavaTokenType.LBRACE,
			JavaTokenType.RBRACE, JavaTokenType.LBRACKET, JavaTokenType.RBRACKET, JavaTokenType.SEMICOLON, JavaTokenType.COMMA, JavaTokenType.DOT, JavaTokenType.ELLIPSIS, JavaTokenType.AT),
			ElementType.OPERATION_BIT_SET);

	public JavaFilterLexer(final Lexer originalLexer, final OccurrenceConsumer table)
	{
		super(originalLexer, table);
	}

	@Override
	public void advance()
	{
		final IElementType tokenType = myDelegate.getTokenType();

		if(tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.LONG_LITERAL || tokenType == JavaTokenType.INTEGER_LITERAL || tokenType == JavaTokenType.CHARACTER_LITERAL || tokenType
				== JavaTokenType.ARROW || tokenType == JavaTokenType.DOUBLE_COLON)
		{
			addOccurrenceInToken(UsageSearchContext.IN_CODE);
		}
		else if(tokenType == JavaTokenType.STRING_LITERAL)
		{
			scanWordsInToken(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, true);
		}
		else if(ElementType.JAVA_COMMENT_BIT_SET.contains(tokenType))
		{
			scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
			advanceTodoItemCountsInToken();
		}
		else if(!ourSkipWordsScanSet.contains(tokenType))
		{
			scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT, false, false);
		}

		myDelegate.advance();
	}
}
