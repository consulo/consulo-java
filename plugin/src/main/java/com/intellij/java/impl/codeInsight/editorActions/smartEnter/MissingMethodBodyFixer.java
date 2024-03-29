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

import static com.intellij.java.language.psi.PsiModifier.ABSTRACT;
import static com.intellij.java.language.psi.PsiModifier.DEFAULT;
import static com.intellij.java.language.psi.PsiModifier.NATIVE;
import static com.intellij.java.language.psi.PsiModifier.PRIVATE;
import static com.intellij.java.language.psi.PsiModifier.STATIC;

import consulo.document.Document;
import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiDeclarationStatement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;

public class MissingMethodBodyFixer implements Fixer
{
	@Override
	public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException
	{
		if(!(psiElement instanceof PsiMethod))
		{
			return;
		}
		PsiMethod method = (PsiMethod) psiElement;
		final PsiClass containingClass = method.getContainingClass();
		if(!shouldHaveBody(method))
		{
			return;
		}

		final PsiCodeBlock body = method.getBody();
		final Document doc = editor.getDocument();
		if(body != null)
		{
			// See IDEADEV-1093. This is quite hacky heuristic but it seem to be best we can do.
			String bodyText = body.getText();
			if(bodyText.startsWith("{"))
			{
				final PsiStatement[] statements = body.getStatements();
				if(statements.length > 0)
				{
					if(statements[0] instanceof PsiDeclarationStatement)
					{
						if(PsiTreeUtil.getDeepestLast(statements[0]) instanceof PsiErrorElement)
						{
							if(containingClass.getRBrace() == null)
							{
								doc.insertString(body.getTextRange().getStartOffset() + 1, "\n}");
							}
						}
					}
				}
			}
			return;
		}
		int endOffset = method.getThrowsList().getTextRange().getEndOffset();
		if(endOffset < doc.getTextLength() && doc.getCharsSequence().charAt(endOffset) == ';')
		{
			doc.deleteString(endOffset, endOffset + 1);
		}
		doc.insertString(endOffset, "{\n}");
	}

	static boolean shouldHaveBody(PsiMethod method)
	{
		PsiClass containingClass = method.getContainingClass();
		if(containingClass == null)
		{
			return false;
		}
		if(method.hasModifierProperty(PRIVATE))
		{
			return true;
		}
		if(method.hasModifierProperty(ABSTRACT) || method.hasModifierProperty(NATIVE))
		{
			return false;
		}
		if(containingClass.isInterface() && !method.hasModifierProperty(DEFAULT) && !method.hasModifierProperty(STATIC))
		{
			return false;
		}
		return true;
	}
}
