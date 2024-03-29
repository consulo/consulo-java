/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.osmorc.manifest.lang;

import java.util.HashMap;
import java.util.Map;

import consulo.language.editor.highlight.LanguageVersionableSyntaxHighlighter;
import consulo.language.lexer.Lexer;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.ast.IElementType;
import consulo.java.manifest.lang.ManifestLanguageVersion;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionWithParsing;
import jakarta.annotation.Nonnull;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestSyntaxHighlighter extends LanguageVersionableSyntaxHighlighter
{
	protected final Map<IElementType, TextAttributesKey> keys = new HashMap<IElementType, TextAttributesKey>();

	public ManifestSyntaxHighlighter(ManifestLanguageVersion languageVersion)
	{
		super(languageVersion);

		safeMap(keys, ManifestTokenType.HEADER_NAME, ManifestColorsAndFonts.HEADER_NAME_KEY);
		safeMap(keys, ManifestTokenType.HEADER_VALUE_PART, ManifestColorsAndFonts.HEADER_VALUE_KEY);
		if(languageVersion == ManifestLanguageVersion.Bnd)
		{
			safeMap(keys, ManifestTokenType.LINE_COMMENT, ManifestColorsAndFonts.LINE_COMMENT_KEY);
		}
	}

	@Override
	public Lexer getHighlightingLexer(LanguageVersion languageVersion)
	{
		return ((LanguageVersionWithParsing) languageVersion).createLexer();
	}

	@Override
	@Nonnull
	public TextAttributesKey[] getTokenHighlights(IElementType tokenType)
	{
		return pack(keys.get(tokenType));
	}
}
