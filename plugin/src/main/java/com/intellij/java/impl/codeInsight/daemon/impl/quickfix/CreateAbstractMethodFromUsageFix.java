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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.util.PsiUtil;

public class CreateAbstractMethodFromUsageFix extends CreateMethodFromUsageFix
{
	public CreateAbstractMethodFromUsageFix(@Nonnull PsiMethodCallExpression methodCall)
	{
		super(methodCall);
	}

	@Override
	protected String getDisplayString(String name)
	{
		return JavaQuickFixBundle.message("create.abstract.method.from.usage.text", name);
	}

	@Nonnull
	@Override
	protected List<PsiClass> getTargetClasses(PsiElement element)
	{
		List<PsiClass> result = new ArrayList<PsiClass>();
		PsiReferenceExpression expr = getMethodCall().getMethodExpression();
		for(PsiClass each : super.getTargetClasses(element))
		{
			if(PsiUtil.isAbstractClass(each) && !each.isInterface() && !shouldCreateStaticMember(expr, each))
			{
				result.add(each);
			}
		}
		return result;
	}

	@Override
	protected String getVisibility(PsiClass parentClass, @Nonnull PsiClass targetClass)
	{
		String result = super.getVisibility(parentClass, targetClass);
		return PsiModifier.PUBLIC.equals(result) ? result : PsiModifier.PROTECTED;
	}

	@Override
	protected boolean shouldBeAbstract(PsiReferenceExpression expression, PsiClass targetClass)
	{
		return true;
	}
}
