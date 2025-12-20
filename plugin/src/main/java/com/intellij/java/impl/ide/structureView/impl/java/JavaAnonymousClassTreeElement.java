/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.java.language.util.JavaAnonymousClassesHelper;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import consulo.ui.image.Image;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassTreeElement extends JavaClassTreeElement
{
	public static final JavaAnonymousClassTreeElement[] EMPTY_ARRAY = {};

	private String myName;
	private String myBaseName;

	public JavaAnonymousClassTreeElement(PsiAnonymousClass aClass)
	{
		super(aClass, false);
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
		PsiClass element = getElement();

		if(element != null)
		{
			myName = JavaAnonymousClassesHelper.getName((PsiAnonymousClass) element);
			if(myName != null)
			{
				return myName;
			}
		}
		return "Anonymous";
	}

	@Override
	public boolean isSearchInLocationString()
	{
		return true;
	}

	@Override
	public String getLocationString()
	{
		if(myBaseName == null)
		{
			PsiAnonymousClass anonymousClass = (PsiAnonymousClass) getElement();
			if(anonymousClass != null)
			{
				myBaseName = anonymousClass.getBaseClassType().getClassName();
			}
		}
		return myBaseName;
	}

	@Override
	public String toString()
	{
		return super.toString() + (myBaseName == null ? "" : " (" + getLocationString() + ")");
	}

	@Override
	public Image getIcon()
	{
		return AllIcons.Nodes.AnonymousClass;
	}
}
