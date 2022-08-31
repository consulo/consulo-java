/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.moveLeftRight;

import javax.annotation.Nonnull;
import consulo.annotation.access.RequiredReadAction;
import com.intellij.java.language.psi.PsiAnnotationParameterList;
import com.intellij.java.language.psi.PsiArrayInitializerExpression;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.psi.util.PsiTreeUtil;

public class JavaMoveLeftRightHandler implements MoveElementLeftRightHandler
{
	@RequiredReadAction
	@Nonnull
	@Override
	public PsiElement[] getMovableSubElements(@Nonnull PsiElement element)
	{
		if(element instanceof PsiParameterList)
		{
			return ((PsiParameterList) element).getParameters();
		}
		else if(element instanceof PsiExpressionList)
		{
			return ((PsiExpressionList) element).getExpressions();
		}
		else if(element instanceof PsiAnnotationParameterList)
		{
			return ((PsiAnnotationParameterList) element).getAttributes();
		}
		else if(element instanceof PsiArrayInitializerExpression)
		{
			return ((PsiArrayInitializerExpression) element).getInitializers();
		}
		else if(element instanceof PsiClass && ((PsiClass) element).isEnum())
		{
			PsiEnumConstant[] enumConstants = PsiTreeUtil.getChildrenOfType(element, PsiEnumConstant.class);
			if(enumConstants != null)
			{
				return enumConstants;
			}
		}
		return PsiElement.EMPTY_ARRAY;
	}
}
