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
package com.intellij.java.language.impl.psi.impl.compiled;

import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiReferenceParameterList;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.impl.psi.impl.cache.TypeAnnotationContainer;
import consulo.language.impl.ast.TreeElement;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClsReferenceParameterListImpl extends ClsElementImpl implements PsiReferenceParameterList
{
	@NonNls
	private static final Pattern EXTENDS_PREFIX = Pattern.compile("^(\\?\\s*extends\\s*)(.*)");
	@NonNls
	private static final Pattern SUPER_PREFIX = Pattern.compile("^(\\?\\s*super\\s*)(.*)");

	private final PsiElement myParent;
	private final ClsTypeElementImpl[] myTypeParameters;
	private volatile PsiType[] myTypeParametersCachedTypes;

	public ClsReferenceParameterListImpl(PsiElement parent,
										 @Nonnull String[] classParameters,
										 @Nonnull TypeAnnotationContainer annotations)
	{
		myParent = parent;

		int length = classParameters.length;
		myTypeParameters = new ClsTypeElementImpl[length];

		for(int i = 0; i < length; i++)
		{
			String s = classParameters[length - i - 1];
			char variance = ClsTypeElementImpl.VARIANCE_NONE;
			final Matcher extendsMatcher = EXTENDS_PREFIX.matcher(s);
			if(extendsMatcher.find())
			{
				variance = ClsTypeElementImpl.VARIANCE_EXTENDS;
				s = extendsMatcher.group(2);
			}
			else
			{
				final Matcher superMatcher = SUPER_PREFIX.matcher(s);
				if(superMatcher.find())
				{
					variance = ClsTypeElementImpl.VARIANCE_SUPER;
					s = superMatcher.group(2);
				}
				else if(StringUtil.startsWithChar(s, '?'))
				{
					variance = ClsTypeElementImpl.VARIANCE_INVARIANT;
					s = s.substring(1);
				}
			}

			myTypeParameters[i] = new ClsTypeElementImpl(this, s, variance, annotations.forTypeArgument(length - i - 1));
		}
	}

	@Override
	public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer)
	{
	}

	@Override
	public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException
	{
	}

	@Override
	@Nonnull
	public PsiTypeElement[] getTypeParameterElements()
	{
		return myTypeParameters;
	}

	@Override
	@Nonnull
	public PsiType[] getTypeArguments()
	{
		PsiType[] cachedTypes = myTypeParametersCachedTypes;
		if(cachedTypes == null)
		{
			cachedTypes = PsiType.createArray(myTypeParameters.length);
			for(int i = 0; i < cachedTypes.length; i++)
			{
				cachedTypes[cachedTypes.length - i - 1] = myTypeParameters[i].getType();
			}
			myTypeParametersCachedTypes = cachedTypes;
		}
		return cachedTypes;
	}

	@Override
	@Nonnull
	public PsiElement[] getChildren()
	{
		return myTypeParameters;
	}

	@Override
	public PsiElement getParent()
	{
		return myParent;
	}
}
