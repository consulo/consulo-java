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
package com.intellij.java.impl.ide.util.projectWizard.importSources;

import com.intellij.java.language.impl.lexer.JavaLexer;
import consulo.language.lexer.Lexer;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import jakarta.annotation.Nullable;


public class JavaSourceRootDetectionUtil
{
	private static final TokenSet JAVA_FILE_FIRST_TOKEN_SET = TokenSet.orSet(ElementType.MODIFIER_BIT_SET, ElementType.CLASS_KEYWORD_BIT_SET, TokenSet.create(JavaTokenType.AT, JavaTokenType
			.IMPORT_KEYWORD));

	private JavaSourceRootDetectionUtil()
	{
	}

	@Nullable
	public static String getPackageName(CharSequence text)
	{
		Lexer lexer = new JavaLexer(LanguageLevel.JDK_1_3);
		lexer.start(text);
		skipWhiteSpaceAndComments(lexer);
		final IElementType firstToken = lexer.getTokenType();
		if(firstToken != JavaTokenType.PACKAGE_KEYWORD)
		{
			if(JAVA_FILE_FIRST_TOKEN_SET.contains(firstToken))
			{
				return "";
			}
			return null;
		}
		lexer.advance();
		skipWhiteSpaceAndComments(lexer);

		final StringBuilder buffer = new StringBuilder();
		while(true)
		{
			if(lexer.getTokenType() != JavaTokenType.IDENTIFIER)
			{
				break;
			}
			buffer.append(text, lexer.getTokenStart(), lexer.getTokenEnd());
			lexer.advance();
			skipWhiteSpaceAndComments(lexer);
			if(lexer.getTokenType() != JavaTokenType.DOT)
			{
				break;
			}
			buffer.append('.');
			lexer.advance();
			skipWhiteSpaceAndComments(lexer);
		}
		String packageName = buffer.toString();
		if(packageName.length() == 0 || StringUtil.endsWithChar(packageName, '.'))
		{
			return null;
		}
		return packageName;
	}

	public static void skipWhiteSpaceAndComments(Lexer lexer)
	{
		while(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(lexer.getTokenType()))
		{
			lexer.advance();
		}
	}
}
