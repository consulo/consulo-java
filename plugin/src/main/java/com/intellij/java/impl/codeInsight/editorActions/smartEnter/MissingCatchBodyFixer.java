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

import consulo.document.Document;
import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaToken;
import consulo.language.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingCatchBodyFixer implements Fixer
{
	@Override
	public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException
	{
		if(!(psiElement instanceof PsiCatchSection))
		{
			return;
		}
		PsiCatchSection catchSection = (PsiCatchSection) psiElement;

		final Document doc = editor.getDocument();

		PsiCodeBlock body = catchSection.getCatchBlock();
		if(body != null && startLine(doc, body) == startLine(doc, catchSection))
		{
			return;
		}

		final PsiJavaToken rParenth = catchSection.getRParenth();
		if(rParenth == null)
		{
			return;
		}

		doc.insertString(rParenth.getTextRange().getEndOffset(), "{}");
	}

	private static int startLine(Document doc, PsiElement psiElement)
	{
		return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
	}
}