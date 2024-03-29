/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import jakarta.annotation.Nullable;

import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import consulo.language.psi.*;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;

/**
 * @author max
 * @since Sep 5, 2003
 */
public class SemicolonFixer implements Fixer
{
	@Override
	public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException
	{
		@SuppressWarnings("unused") boolean b = fixReturn(editor, psiElement) || fixForUpdate(editor, psiElement) || fixAfterLastValidElement(editor, psiElement);
	}

	private static boolean fixReturn(@Nonnull Editor editor, @Nullable PsiElement psiElement)
	{
		if(psiElement instanceof PsiReturnStatement)
		{
			PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class, true, PsiLambdaExpression.class);
			if(method != null && PsiType.VOID.equals(method.getReturnType()))
			{
				PsiReturnStatement stmt = (PsiReturnStatement) psiElement;
				if(stmt.getReturnValue() != null)
				{
					Document doc = editor.getDocument();
					doc.insertString(stmt.getTextRange().getStartOffset() + "return".length(), ";");
					return true;
				}
			}
		}
		return false;
	}

	private static boolean fixForUpdate(@Nonnull Editor editor, @Nullable PsiElement psiElement)
	{
		if(!(psiElement instanceof PsiForStatement))
		{
			return false;
		}

		PsiForStatement forStatement = (PsiForStatement) psiElement;
		PsiExpression condition = forStatement.getCondition();
		if(forStatement.getUpdate() != null || condition == null)
		{
			return false;
		}

		TextRange range = condition.getTextRange();
		Document document = editor.getDocument();
		CharSequence text = document.getCharsSequence();
		for(int i = range.getEndOffset() - 1, max = forStatement.getTextRange().getEndOffset(); i < max; i++)
		{
			if(text.charAt(i) == ';')
			{
				return false;
			}
		}

		String toInsert = ";";
		if(CodeStyleSettingsManager.getSettings(psiElement.getProject()).SPACE_AFTER_SEMICOLON)
		{
			toInsert += " ";
		}
		document.insertString(range.getEndOffset(), toInsert);
		return true;
	}

	private static boolean fixAfterLastValidElement(@Nonnull Editor editor, @Nullable PsiElement psiElement)
	{
		if(psiElement instanceof PsiExpressionStatement  ||
				psiElement instanceof PsiDeclarationStatement ||
				psiElement instanceof PsiImportStatementBase ||
				psiElement instanceof PsiDoWhileStatement ||
				psiElement instanceof PsiReturnStatement ||
				psiElement instanceof PsiThrowStatement ||
				psiElement instanceof PsiBreakStatement || psiElement instanceof PsiContinueStatement ||
				psiElement instanceof PsiAssertStatement ||
				psiElement instanceof PsiPackageStatement ||
				psiElement instanceof PsiField && !(psiElement instanceof PsiEnumConstant) ||
				psiElement instanceof PsiMethod && ((PsiMethod) psiElement).getBody() == null && !MissingMethodBodyFixer.shouldHaveBody((PsiMethod) psiElement) ||
				psiElement instanceof  PsiRequiresStatement ||
				psiElement instanceof PsiPackageAccessibilityStatement ||
				psiElement instanceof PsiUsesStatement ||
				psiElement instanceof PsiProvidesStatement)
		{
			String text = psiElement.getText();

			int tailLength = 0;
			ASTNode leaf = TreeUtil.findLastLeaf(psiElement.getNode());
			while(leaf != null && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(leaf.getElementType()))
			{
				tailLength += leaf.getTextLength();
				leaf = TreeUtil.prevLeaf(leaf);
			}
			if(leaf == null)
			{
				return false;
			}

			if(tailLength > 0)
			{
				text = text.substring(0, text.length() - tailLength);
			}

			int insertionOffset = leaf.getTextRange().getEndOffset();
			Document doc = editor.getDocument();
			if(psiElement instanceof PsiField && ((PsiField) psiElement).hasModifierProperty(PsiModifier.ABSTRACT))
			{
				// abstract rarely seem to be field. It is rather incomplete method.
				doc.insertString(insertionOffset, "()");
				insertionOffset += "()".length();
			}

			if(!StringUtil.endsWithChar(text, ';'))
			{
				PsiElement parent = psiElement.getParent();
				String toInsert = ";";
				if(parent instanceof PsiForStatement)
				{
					if(((PsiForStatement) parent).getUpdate() == psiElement)
					{
						return false;
					}
					if(CodeStyleSettingsManager.getSettings(psiElement.getProject()).SPACE_AFTER_SEMICOLON)
					{
						toInsert += " ";
					}
				}

				doc.insertString(insertionOffset, toInsert);
				return true;
			}
		}

		return false;
	}
}