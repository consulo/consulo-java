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
package com.intellij.java.impl.ide.structureView.impl.java;

import consulo.application.AllIcons;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import com.intellij.java.language.util.PsiLambdaNameHelper;
import consulo.project.DumbService;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;

public class JavaLambdaTreeElement extends JavaClassTreeElementBase<PsiLambdaExpression>
{
	public static final JavaLambdaTreeElement[] EMPTY_ARRAY = {};

	private String myName;
	private String myFunctionalName;

	public JavaLambdaTreeElement(PsiLambdaExpression lambdaExpression)
	{
		super(false, lambdaExpression);
	}

	@Override
	public boolean isPublic()
	{
		return false;
	}

	@Override
	public String getPresentableText()
	{
		if(myName != null)
		{
			return myName;
		}
		final PsiLambdaExpression element = getElement();

		if(element != null)
		{
			myName = PsiLambdaNameHelper.getVMName(element);
			return myName;
		}
		return "Lambda";
	}


	@Override
	public boolean isSearchInLocationString()
	{
		return true;
	}

	@Override
	public String getLocationString()
	{
		if(myFunctionalName == null)
		{
			PsiLambdaExpression lambdaExpression = getElement();
			if(lambdaExpression != null && !DumbService.isDumb(lambdaExpression.getProject()))
			{
				final PsiType interfaceType = lambdaExpression.getFunctionalInterfaceType();
				if(interfaceType != null)
				{
					myFunctionalName = interfaceType.getPresentableText();
				}
			}
		}
		return myFunctionalName;
	}

	@Override
	public String toString()
	{
		return super.toString() + (myFunctionalName == null ? "" : " (" + getLocationString() + ")");
	}

	@Nonnull
	@Override
	public Collection<StructureViewTreeElement> getChildrenBase()
	{
		return Collections.emptyList();
	}

	@Override
	public Image getIcon()
	{
		return AllIcons.Nodes.Lambda;
	}
}
