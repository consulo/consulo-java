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

import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import consulo.language.ast.IElementType;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestTokenType extends IElementType
{
	public static final ManifestTokenType HEADER_NAME = new ManifestTokenType("HEADER_NAME_TOKEN");
	public static final ManifestTokenType NEWLINE = new ManifestTokenType("NEWLINE_TOKEN");
	public static final ManifestTokenType SECTION_END = new ManifestTokenType("SECTION_END_TOKEN");
	public static final ManifestTokenType COLON = new ManifestTokenType("COLON_TOKEN");
	public static final ManifestTokenType COLON_EQUALS = new ManifestTokenType("COLON_EQUALS");
	public static final ManifestTokenType SEMICOLON = new ManifestTokenType("SEMICOLON_TOKEN");
	public static final ManifestTokenType EQUALS = new ManifestTokenType("EQUALS_TOKEN");
	public static final ManifestTokenType COMMA = new ManifestTokenType("COMMA_TOKEN");
	public static final ManifestTokenType SHARP = new ManifestTokenType("SHARP");
	public static final ManifestTokenType QUOTE = new ManifestTokenType("QUOTE_TOKEN");
	public static final ManifestTokenType HEADER_VALUE_PART = new ManifestTokenType("HEADER_VALUE_PART_TOKEN");
	public static final ManifestTokenType SIGNIFICANT_SPACE = new ManifestTokenType("SIGNIFICANT_SPACE_TOKEN");
	public static final ManifestTokenType OPENING_PARENTHESIS_TOKEN = new ManifestTokenType("OPENING_PARENTHESIS_TOKEN");
	public static final ManifestTokenType CLOSING_PARENTHESIS_TOKEN = new ManifestTokenType("CLOSING_PARENTHESIS_TOKEN");
	public static final ManifestTokenType OPENING_BRACKET_TOKEN = new ManifestTokenType("OPENING_BRACKET_TOKEN");
	public static final ManifestTokenType CLOSING_BRACKET_TOKEN = new ManifestTokenType("CLOSING_BRACKET_TOKEN");
	public static final ManifestTokenType LINE_COMMENT = new ManifestTokenType("LINE_COMMENT");

	public ManifestTokenType(@Nonnull @NonNls String debugName)
	{
		super(debugName, ManifestLanguage.INSTANCE);
	}

	public String toString()
	{
		return "ManifestTokenType: " + super.toString();
	}
}
