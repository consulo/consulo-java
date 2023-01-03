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
package com.intellij.java.analysis.impl.ide.highlighter;

import consulo.codeEditor.DefaultLanguageHighlighterColors;
import consulo.colorScheme.TextAttributesKey;

/**
 * Highlighting text attributes for Java language.
 *
 * @author Rustam Vishnyakov
 */
public interface JavaHighlightingColors
{
	TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
	TextAttributesKey JAVA_BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
	TextAttributesKey DOC_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_DOC_COMMENT", DefaultLanguageHighlighterColors.DOC_COMMENT);
	TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey("JAVA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
	TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey("JAVA_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
	TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey("JAVA_STRING", DefaultLanguageHighlighterColors.STRING);
	TextAttributesKey OPERATION_SIGN = TextAttributesKey.createTextAttributesKey("JAVA_OPERATION_SIGN", DefaultLanguageHighlighterColors.OPERATION_SIGN);
	TextAttributesKey PARENTHESES = TextAttributesKey.createTextAttributesKey("JAVA_PARENTH", DefaultLanguageHighlighterColors.PARENTHESES);
	TextAttributesKey BRACKETS = TextAttributesKey.createTextAttributesKey("JAVA_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
	TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey("JAVA_BRACES", DefaultLanguageHighlighterColors.BRACES);
	TextAttributesKey COMMA = TextAttributesKey.createTextAttributesKey("JAVA_COMMA", DefaultLanguageHighlighterColors.COMMA);
	TextAttributesKey DOT = TextAttributesKey.createTextAttributesKey("JAVA_DOT", DefaultLanguageHighlighterColors.DOT);
	TextAttributesKey JAVA_SEMICOLON = TextAttributesKey.createTextAttributesKey("JAVA_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);
	TextAttributesKey DOC_COMMENT_TAG = TextAttributesKey.createTextAttributesKey("JAVA_DOC_TAG", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
	TextAttributesKey DOC_COMMENT_MARKUP = TextAttributesKey.createTextAttributesKey("JAVA_DOC_MARKUP", DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP);
	TextAttributesKey DOC_COMMENT_TAG_VALUE = TextAttributesKey.createTextAttributesKey("DOC_COMMENT_TAG_VALUE", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE);
	TextAttributesKey VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("JAVA_VALID_STRING_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
	TextAttributesKey INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("JAVA_INVALID_STRING_ESCAPE", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE);

	TextAttributesKey LOCAL_VARIABLE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("LOCAL_VARIABLE_ATTRIBUTES", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
	TextAttributesKey PARAMETER_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("PARAMETER_ATTRIBUTES", DefaultLanguageHighlighterColors.PARAMETER);
	TextAttributesKey LAMBDA_PARAMETER_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("LAMBDA_PARAMETER_ATTRIBUTES", PARAMETER_ATTRIBUTES);
	TextAttributesKey REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES", LOCAL_VARIABLE_ATTRIBUTES);
	TextAttributesKey REASSIGNED_PARAMETER_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("REASSIGNED_PARAMETER_ATTRIBUTES", PARAMETER_ATTRIBUTES);
	TextAttributesKey INSTANCE_FIELD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INSTANCE_FIELD_ATTRIBUTES", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
	TextAttributesKey INSTANCE_FINAL_FIELD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INSTANCE_FINAL_FIELD_ATTRIBUTES", INSTANCE_FIELD_ATTRIBUTES);
	TextAttributesKey STATIC_FIELD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("STATIC_FIELD_ATTRIBUTES", DefaultLanguageHighlighterColors.STATIC_FIELD);
	TextAttributesKey STATIC_FINAL_FIELD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("STATIC_FINAL_FIELD_ATTRIBUTES", STATIC_FIELD_ATTRIBUTES);
	TextAttributesKey CLASS_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CLASS_NAME_ATTRIBUTES", DefaultLanguageHighlighterColors.CLASS_NAME);
	TextAttributesKey PACKAGE_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("PACKAGE_NAME_ATTRIBUTES", DefaultLanguageHighlighterColors.IDENTIFIER);
	TextAttributesKey ANONYMOUS_CLASS_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ANONYMOUS_CLASS_NAME_ATTRIBUTES", CLASS_NAME_ATTRIBUTES);
	TextAttributesKey IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES", PARAMETER_ATTRIBUTES);
	TextAttributesKey TYPE_PARAMETER_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("TYPE_PARAMETER_NAME_ATTRIBUTES", DefaultLanguageHighlighterColors.TYPE_ALIAS_NAME);
	TextAttributesKey INTERFACE_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INTERFACE_NAME_ATTRIBUTES", DefaultLanguageHighlighterColors.INTERFACE_NAME);
	TextAttributesKey ENUM_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ENUM_NAME_ATTRIBUTES", CLASS_NAME_ATTRIBUTES);
	TextAttributesKey ABSTRACT_CLASS_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ABSTRACT_CLASS_NAME_ATTRIBUTES", CLASS_NAME_ATTRIBUTES);
	TextAttributesKey METHOD_CALL_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("METHOD_CALL_ATTRIBUTES", DefaultLanguageHighlighterColors.FUNCTION_CALL);
	TextAttributesKey METHOD_DECLARATION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("METHOD_DECLARATION_ATTRIBUTES", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
	TextAttributesKey STATIC_METHOD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("STATIC_METHOD_ATTRIBUTES", DefaultLanguageHighlighterColors.STATIC_METHOD);
	TextAttributesKey ABSTRACT_METHOD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ABSTRACT_METHOD_ATTRIBUTES", METHOD_CALL_ATTRIBUTES);
	TextAttributesKey INHERITED_METHOD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INHERITED_METHOD_ATTRIBUTES", METHOD_CALL_ATTRIBUTES);
	TextAttributesKey CONSTRUCTOR_CALL_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CONSTRUCTOR_CALL_ATTRIBUTES", DefaultLanguageHighlighterColors.CLASS_REFERENCE);
	TextAttributesKey CONSTRUCTOR_DECLARATION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CONSTRUCTOR_DECLARATION_ATTRIBUTES", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
	TextAttributesKey ANNOTATION_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ANNOTATION_NAME_ATTRIBUTES", DefaultLanguageHighlighterColors.METADATA);
	TextAttributesKey ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES", DefaultLanguageHighlighterColors.METADATA);
	TextAttributesKey ANNOTATION_ATTRIBUTE_VALUE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ANNOTATION_ATTRIBUTE_VALUE_ATTRIBUTES", DefaultLanguageHighlighterColors.METADATA);
}
