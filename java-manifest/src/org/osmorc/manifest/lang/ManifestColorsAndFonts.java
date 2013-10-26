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

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public interface ManifestColorsAndFonts
{
	TextAttributesKey HEADER_NAME_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_HEADER_NAME_KEY", DefaultLanguageHighlighterColors.KEYWORD);
	TextAttributesKey HEADER_VALUE_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_HEADER_VALUE_KEY", DefaultLanguageHighlighterColors.STRING);
	TextAttributesKey DIRECTIVE_NAME_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_DIRECTIVE_NAME_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
	TextAttributesKey ATTRIBUTE_NAME_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_ATTRIBUTE_NAME_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
	TextAttributesKey HEADER_ASSIGNMENT_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_HEADER_ASSIGNMENT_KEY", DefaultLanguageHighlighterColors.OPERATION_SIGN);
	TextAttributesKey ATTRIBUTE_ASSIGNMENT_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_ATTRIBUTE_ASSIGNMENT_KEY", DefaultLanguageHighlighterColors.OPERATION_SIGN);
	TextAttributesKey DIRECTIVE_ASSIGNMENT_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_DIRECTIVE_ASSIGNMENT_KEY", DefaultLanguageHighlighterColors.OPERATION_SIGN);
	TextAttributesKey CLAUSE_SEPARATOR_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_CLAUSE_SEPARATOR_KEY", DefaultLanguageHighlighterColors.COMMA);
	TextAttributesKey PARAMETER_SEPARATOR_KEY = TextAttributesKey.createTextAttributesKey("MANIFEST_PARAMETER_SEPARATOR_KEY", DefaultLanguageHighlighterColors.SEMICOLON);
	TextAttributesKey LINE_COMMENT_KEY = TextAttributesKey.createTextAttributesKey("BND_LINE_COMMENT_KEY", DefaultLanguageHighlighterColors.LINE_COMMENT);
}
