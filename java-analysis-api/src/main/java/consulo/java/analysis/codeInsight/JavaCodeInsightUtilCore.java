/*
 * Copyright 2013-2017 consulo.io
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

package consulo.java.analysis.codeInsight;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.java.language.psi.*;
import com.intellij.lang.Language;
import com.intellij.java.language.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Set;

/**
 * @author VISTALL
 * @since 28-Dec-17
 */
public class JavaCodeInsightUtilCore
{
	@Nullable
	public static PsiExpression findExpressionInRange(PsiFile file, int startOffset, int endOffset)
	{
		if(!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE))
		{
			return null;
		}
		PsiExpression expression = findElementInRange(file, startOffset, endOffset, PsiExpression.class);
		if(expression == null && findStatementsInRange(file, startOffset, endOffset).length == 0)
		{
			PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, JavaLanguage.INSTANCE);
			if(element2 instanceof PsiJavaToken)
			{
				final PsiJavaToken token = (PsiJavaToken) element2;
				final IElementType tokenType = token.getTokenType();
				if(tokenType.equals(JavaTokenType.SEMICOLON))
				{
					expression = findElementInRange(file, startOffset, element2.getTextRange().getStartOffset(), PsiExpression.class);
				}
			}
		}
		if(expression == null && findStatementsInRange(file, startOffset, endOffset).length == 0)
		{
			PsiElement element = PsiTreeUtil.skipSiblingsBackward(file.findElementAt(endOffset), PsiWhiteSpace.class);
			if(element != null)
			{
				element = PsiTreeUtil.skipSiblingsBackward(element.getLastChild(), PsiWhiteSpace.class, PsiComment.class);
				if(element != null)
				{
					final int newEndOffset = element.getTextRange().getEndOffset();
					if(newEndOffset < endOffset)
					{
						expression = findExpressionInRange(file, startOffset, newEndOffset);
					}
				}
			}
		}
		if(expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression)
		{
			return null;
		}
		return expression;
	}

	public static <T extends PsiElement> T findElementInRange(PsiFile file, int startOffset, int endOffset, Class<T> klass)
	{
		return CodeInsightUtilCore.findElementInRange(file, startOffset, endOffset, klass, JavaLanguage.INSTANCE);
	}

	@Nonnull
	public static PsiElement[] findStatementsInRange(@Nonnull PsiFile file, int startOffset, int endOffset)
	{
		Language language = findJavaOrLikeLanguage(file);
		if(language == null)
		{
			return PsiElement.EMPTY_ARRAY;
		}
		FileViewProvider viewProvider = file.getViewProvider();
		PsiElement element1 = viewProvider.findElementAt(startOffset, language);
		PsiElement element2 = viewProvider.findElementAt(endOffset - 1, language);
		if(element1 instanceof PsiWhiteSpace)
		{
			startOffset = element1.getTextRange().getEndOffset();
			element1 = file.findElementAt(startOffset);
		}
		if(element2 instanceof PsiWhiteSpace)
		{
			endOffset = element2.getTextRange().getStartOffset();
			element2 = file.findElementAt(endOffset - 1);
		}
		if(element1 == null || element2 == null)
		{
			return PsiElement.EMPTY_ARRAY;
		}

		PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
		if(parent == null)
		{
			return PsiElement.EMPTY_ARRAY;
		}
		while(true)
		{
			if(parent instanceof PsiStatement)
			{
				if(!(element1 instanceof PsiComment))
				{
					parent = parent.getParent();
				}
				break;
			}
			if(parent instanceof PsiCodeBlock)
			{
				break;
			}
			/*if(FileTypeUtils.isInServerPageFile(parent) && parent instanceof PsiFile)
			{
				break;
			}   */
			if(parent instanceof PsiCodeFragment)
			{
				break;
			}
			if(parent == null || parent instanceof PsiFile)
			{
				return PsiElement.EMPTY_ARRAY;
			}
			parent = parent.getParent();
		}

		if(!parent.equals(element1))
		{
			while(!parent.equals(element1.getParent()))
			{
				element1 = element1.getParent();
			}
		}
		if(startOffset != element1.getTextRange().getStartOffset())
		{
			return PsiElement.EMPTY_ARRAY;
		}

		if(!parent.equals(element2))
		{
			while(!parent.equals(element2.getParent()))
			{
				element2 = element2.getParent();
			}
		}
		if(endOffset != element2.getTextRange().getEndOffset())
		{
			return PsiElement.EMPTY_ARRAY;
		}

		if(parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement && element1 == ((PsiCodeBlock) parent).getLBrace() && element2 == ((PsiCodeBlock) parent).getRBrace())
		{
			return new PsiElement[]{parent.getParent()};
		}

/*
	if(parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement) {
      return new PsiElement[]{parent.getParent()};
    }
*/

		PsiElement[] children = parent.getChildren();
		ArrayList<PsiElement> array = new ArrayList<>();
		boolean flag = false;
		for(PsiElement child : children)
		{
			if(child.equals(element1))
			{
				flag = true;
			}
			if(flag && !(child instanceof PsiWhiteSpace))
			{
				array.add(child);
			}
			if(child.equals(element2))
			{
				break;
			}
		}

		for(PsiElement element : array)
		{
			if(!(element instanceof PsiStatement || element instanceof PsiWhiteSpace || element instanceof PsiComment))
			{
				return PsiElement.EMPTY_ARRAY;
			}
		}

		return PsiUtilCore.toPsiElementArray(array);
	}

	@javax.annotation.Nullable
	public static Language findJavaOrLikeLanguage(@Nonnull final PsiFile file)
	{
		final Set<Language> languages = file.getViewProvider().getLanguages();
		for(final Language language : languages)
		{
			if(language == JavaLanguage.INSTANCE)
			{
				return language;
			}
		}
		for(final Language language : languages)
		{
			if(language.isKindOf(JavaLanguage.INSTANCE))
			{
				return language;
			}
		}
		return null;
	}
}
