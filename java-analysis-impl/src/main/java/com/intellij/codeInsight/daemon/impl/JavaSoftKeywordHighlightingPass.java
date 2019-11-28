/*
 * Copyright 2013-2017 must-be.org
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

package com.intellij.codeInsight.daemon.impl;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.lexer.JavaLexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.util.containers.ContainerUtil;
import consulo.annotation.access.RequiredReadAction;

/**
 * @author VISTALL
 * @since 09-Jan-17
 */
public class JavaSoftKeywordHighlightingPass extends TextEditorHighlightingPass
{
	private final PsiJavaFile myFile;
	private final List<HighlightInfo> myResults = new ArrayList<>();

	public JavaSoftKeywordHighlightingPass(@Nonnull PsiJavaFile file, @Nullable Document document)
	{
		super(file.getProject(), document);
		myFile = file;
	}

	@RequiredReadAction
	@Override
	public void doCollectInformation(@Nonnull ProgressIndicator progressIndicator)
	{
		LanguageLevel languageLevel = myFile.getLanguageLevel();

		myFile.accept(new JavaRecursiveElementVisitor()
		{
			@Override
			public void visitKeyword(PsiKeyword keyword)
			{
				if(JavaLexer.isSoftKeyword(keyword.getNode().getChars(), languageLevel))
				{
					ContainerUtil.addIfNotNull(myResults, HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.JAVA_KEYWORD).range(keyword).create());
				}
			}
		});
	}

	@Override
	public void doApplyInformationToEditor()
	{
		if(!myResults.isEmpty())
		{
			UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), myResults, getColorsScheme(), getId());
		}
	}
}
