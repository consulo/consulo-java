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
import com.intellij.java.language.psi.PsiBlockStatement;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiForStatement;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingForBodyFixer implements Fixer
{
	@Override
	public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException
	{
		PsiForStatement forStatement = getForStatementParent(psiElement);
		if(forStatement == null)
		{
			return;
		}

		final Document doc = editor.getDocument();

		PsiElement body = forStatement.getBody();
		if(body instanceof PsiBlockStatement)
		{
			return;
		}
		if(body != null && startLine(doc, body) == startLine(doc, forStatement))
		{
			return;
		}

		PsiElement eltToInsertAfter = forStatement.getRParenth();
		String text = "{}";
		if(eltToInsertAfter == null)
		{
			eltToInsertAfter = forStatement;
			text = "){}";
		}
		doc.insertString(eltToInsertAfter.getTextRange().getEndOffset(), text);
	}

	@Nullable
	private static PsiForStatement getForStatementParent(PsiElement psiElement)
	{
		PsiForStatement statement = PsiTreeUtil.getParentOfType(psiElement, PsiForStatement.class);
		if(statement == null)
		{
			return null;
		}

		PsiStatement init = statement.getInitialization();
		PsiStatement update = statement.getUpdate();
		PsiExpression check = statement.getCondition();

		return isValidChild(init, psiElement) || isValidChild(update, psiElement) || isValidChild(check, psiElement) ? statement : null;
	}

	private static boolean isValidChild(PsiElement ancestor, PsiElement psiElement)
	{
		if(ancestor != null)
		{
			if(PsiTreeUtil.isAncestor(ancestor, psiElement, false))
			{
				if(PsiTreeUtil.hasErrorElements(ancestor))
				{
					return false;
				}
				return true;
			}
		}

		return false;
	}

	private static int startLine(Document doc, PsiElement psiElement)
	{
		return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
	}
}
