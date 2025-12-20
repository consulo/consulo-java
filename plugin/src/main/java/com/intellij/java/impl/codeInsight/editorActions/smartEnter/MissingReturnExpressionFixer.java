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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;

@SuppressWarnings({"HardCodedStringLiteral"})
public class MissingReturnExpressionFixer implements Fixer
{
	@Override
	public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException
	{
		if(!(psiElement instanceof PsiReturnStatement))
		{
			return;
		}
		if(!PsiTreeUtil.hasErrorElements(psiElement))
		{
			return;
		}

		PsiReturnStatement retStatement = (PsiReturnStatement) psiElement;
		if(fixMethodCallWithoutTrailingSemicolon(retStatement, editor, processor))
		{
			return;
		}

		PsiExpression returnValue = retStatement.getReturnValue();
		if(returnValue != null && lineNumber(editor, editor.getCaretModel().getOffset()) == lineNumber(editor, returnValue.getTextRange().getStartOffset()))
		{
			return;
		}

		PsiElement parent = PsiTreeUtil.getParentOfType(psiElement, PsiClassInitializer.class, PsiMethod.class);
		if(parent instanceof PsiMethod)
		{
			PsiType returnType = ((PsiMethod) parent).getReturnType();
			if(returnType != null && !PsiType.VOID.equals(returnType))
			{
				int startOffset = retStatement.getTextRange().getStartOffset();
				if(returnValue != null)
				{
					editor.getDocument().insertString(startOffset + "return".length(), ";");
				}

				processor.registerUnresolvedError(startOffset + "return".length());
			}
		}
	}

	private static boolean fixMethodCallWithoutTrailingSemicolon(@Nullable PsiReturnStatement returnStatement, @Nonnull Editor editor, @Nonnull JavaSmartEnterProcessor processor)
	{
		if(returnStatement == null)
		{
			return false;
		}
		PsiElement lastChild = returnStatement.getLastChild();
		if(!(lastChild instanceof PsiErrorElement))
		{
			return false;
		}
		PsiElement prev = lastChild.getPrevSibling();
		if(prev instanceof PsiWhiteSpace)
		{
			prev = prev.getPrevSibling();
		}

		if(!(prev instanceof PsiJavaToken))
		{
			int offset = returnStatement.getTextRange().getEndOffset();
			PsiMethod method = PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class, true, PsiLambdaExpression.class);
			if(method != null && PsiType.VOID.equals(method.getReturnType()))
			{
				offset = returnStatement.getTextRange().getStartOffset() + "return".length();
			}
			editor.getDocument().insertString(offset, ";");
			//processor.setSkipEnter(true);
			return true;
		}

		PsiJavaToken prevToken = (PsiJavaToken) prev;
		if(prevToken.getTokenType() == JavaTokenType.SEMICOLON)
		{
			return false;
		}

		int offset = returnStatement.getTextRange().getEndOffset();
		editor.getDocument().insertString(offset, ";");
		if(prevToken.getTokenType() == JavaTokenType.RETURN_KEYWORD)
		{
			PsiMethod method = PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class);
			if(method != null && !PsiType.VOID.equals(method.getReturnType()))
			{
				editor.getCaretModel().moveToOffset(offset);
				processor.setSkipEnter(true);
			}
		}
		return true;
	}

	private static int lineNumber(Editor editor, int offset)
	{
		return editor.getDocument().getLineNumber(offset);
	}
}
