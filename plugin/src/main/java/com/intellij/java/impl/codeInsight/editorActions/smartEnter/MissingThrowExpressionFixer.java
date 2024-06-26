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
package com.intellij.java.impl.codeInsight.editorActions.smartEnter;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiThrowStatement;
import consulo.language.util.IncorrectOperationException;

@SuppressWarnings({"HardCodedStringLiteral"})
public class MissingThrowExpressionFixer implements Fixer
{
	@Override
	public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException
	{
		if(psiElement instanceof PsiThrowStatement)
		{
			PsiThrowStatement throwStatement = (PsiThrowStatement) psiElement;
			if(throwStatement.getException() != null && startLine(editor, throwStatement) == startLine(editor, throwStatement.getException()))
			{
				return;
			}

			final int startOffset = throwStatement.getTextRange().getStartOffset();
			if(throwStatement.getException() != null)
			{
				editor.getDocument().insertString(startOffset + "throw".length(), ";");
			}
			processor.registerUnresolvedError(startOffset + "throw".length());
		}
	}

	private int startLine(Editor editor, PsiElement psiElement)
	{
		return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
	}
}
