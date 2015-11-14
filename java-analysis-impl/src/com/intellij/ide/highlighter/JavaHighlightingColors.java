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
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * Highlighting text attributes for Java language.
 *
 * @author Rustam Vishnyakov
 */
public interface JavaHighlightingColors
{
	TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
	TextAttributesKey BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
	TextAttributesKey DOC_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_DOC_COMMENT", DefaultLanguageHighlighterColors.DOC_COMMENT);
	TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey("JAVA_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
	TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey("JAVA_STRING", DefaultLanguageHighlighterColors.STRING);
	TextAttributesKey OPERATION_SIGN = TextAttributesKey.createTextAttributesKey("JAVA_PARENTHS", DefaultLanguageHighlighterColors.OPERATION_SIGN);
	TextAttributesKey PARENTHS = TextAttributesKey.createTextAttributesKey("JAVA_PARENTHS", DefaultLanguageHighlighterColors.PARENTHESES);
	TextAttributesKey BRACKETS = TextAttributesKey.createTextAttributesKey("JAVA_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
	TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey("JAVA_BRACES", DefaultLanguageHighlighterColors.BRACES);
	TextAttributesKey COMMA = TextAttributesKey.createTextAttributesKey("JAVA_COMMA", DefaultLanguageHighlighterColors.COMMA);
	TextAttributesKey DOT = TextAttributesKey.createTextAttributesKey("JAVA_DOT", DefaultLanguageHighlighterColors.DOT);
	TextAttributesKey SEMICOLON = TextAttributesKey.createTextAttributesKey("JAVA_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);
	TextAttributesKey DOC_COMMENT_TAG = TextAttributesKey.createTextAttributesKey("JAVA_DOC_COMMENT_TAG", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
	TextAttributesKey DOC_COMMENT_MARKUP = TextAttributesKey.createTextAttributesKey("JAVA_DOC_COMMENT_MARKUP", DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP);
	TextAttributesKey VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("JAVA_VALID_STRING_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
	TextAttributesKey INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("JAVA_INVALID_STRING_ESCAPE", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE);
	TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey("JAVA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

	TextAttributesKey ANNOTATION_NAME = TextAttributesKey.createTextAttributesKey("Java_ANNOTATION_NAME", DefaultLanguageHighlighterColors.METADATA);
	TextAttributesKey ENUM_NAME = TextAttributesKey.createTextAttributesKey("JAVA_ENUM_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
	TextAttributesKey CLASS_NAME = TextAttributesKey.createTextAttributesKey("JAVA_CLASS_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
	TextAttributesKey INTERFACE_NAME = TextAttributesKey.createTextAttributesKey("JAVA_INTERFACE_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
	TextAttributesKey ANONYMOUS_CLASS_NAME = TextAttributesKey.createTextAttributesKey("JAVA_ANONYMOUS_CLASS_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
	TextAttributesKey TYPE_PARAMETER_NAME = TextAttributesKey.createTextAttributesKey("JAVA_TYPE_PARAMETER_NAME", DefaultLanguageHighlighterColors.TYPE_ALIAS_NAME);
	TextAttributesKey ABSTRACT_CLASS_NAME = TextAttributesKey.createTextAttributesKey("JAVA_ABSTRACT_CLASS_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);

	TextAttributesKey LOCAL_VARIABLE = TextAttributesKey.createTextAttributesKey("JAVA_LOCAL_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
	TextAttributesKey PARAMETER = TextAttributesKey.createTextAttributesKey("JAVA_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);
	TextAttributesKey FIELD = TextAttributesKey.createTextAttributesKey("JAVA_FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
	TextAttributesKey STATIC_FIELD = TextAttributesKey.createTextAttributesKey("JAVA_STATIC_FIELD", DefaultLanguageHighlighterColors.STATIC_FIELD);
	TextAttributesKey STATIC_FINAL_FIELD = TextAttributesKey.createTextAttributesKey("JAVA_STATIC_FINAL_FIELD", STATIC_FIELD);
	TextAttributesKey INSTANCE_FIELD = TextAttributesKey.createTextAttributesKey("JAVA_INSTANCE_FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

	TextAttributesKey REASSIGNED_LOCAL_VARIABLE = TextAttributesKey.createTextAttributesKey("JAVA_REASSIGNED_LOCAL_VARIABLE");
	TextAttributesKey REASSIGNED_PARAMETER = TextAttributesKey.createTextAttributesKey("JAVA_REASSIGNED_PARAMETER");
}
