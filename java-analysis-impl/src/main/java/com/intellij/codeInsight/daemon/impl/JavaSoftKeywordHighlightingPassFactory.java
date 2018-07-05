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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

/**
 * @author VISTALL
 * @since 09-Jan-17
 */
public class JavaSoftKeywordHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory
{
	public JavaSoftKeywordHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar registrar)
	{
		super(project);
		registrar.registerTextEditorHighlightingPass(this, null, null, false, -1);
	}

	@Nullable
	@Override
	public TextEditorHighlightingPass createHighlightingPass(@Nonnull PsiFile file, @Nonnull Editor editor)
	{
		if(file instanceof PsiJavaFile && ((PsiJavaFile) file).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9))
		{
			return new JavaSoftKeywordHighlightingPass((PsiJavaFile) file, editor.getDocument());
		}
		else
		{
			return null;
		}
	}
}
