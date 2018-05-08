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
package com.intellij.codeInsight.lookup;

import java.util.Collections;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import consulo.awt.TargetAWT;
import consulo.ide.IconDescriptorUpdaters;
import consulo.ui.image.Image;

/**
 * @author peter
 */
public class ExpressionLookupItem extends LookupElement implements TypedLookupItem
{
	private final PsiExpression myExpression;
	private final Image myIcon;
	private final String myPresentableText;
	private final String myLookupString;
	private final Set<String> myAllLookupStrings;

	public ExpressionLookupItem(final PsiExpression expression)
	{
		this(expression, getExpressionIcon(expression), expression.getText(), expression.getText());
	}

	public ExpressionLookupItem(final PsiExpression expression, @Nullable Image icon, String presentableText, String... lookupStrings)
	{
		myExpression = expression;
		myPresentableText = presentableText;
		myIcon = icon;
		myLookupString = lookupStrings[0];
		myAllLookupStrings = Collections.unmodifiableSet(ContainerUtil.newHashSet(lookupStrings));
	}

	@Nullable
	private static Image getExpressionIcon(@Nonnull PsiExpression expression)
	{
		if(expression instanceof PsiReferenceExpression)
		{
			final PsiElement element = ((PsiReferenceExpression) expression).resolve();
			if(element != null)
			{
				return IconDescriptorUpdaters.getIcon(element, 0);
			}
		}
		if(expression instanceof PsiMethodCallExpression)
		{
			return AllIcons.Nodes.Method;
		}
		return null;
	}

	@Nonnull
	@Override
	public PsiExpression getObject()
	{
		return myExpression;
	}

	@Override
	public void renderElement(LookupElementPresentation presentation)
	{
		presentation.setIcon(TargetAWT.to(myIcon));
		presentation.setItemText(myPresentableText);
		PsiType type = getType();
		presentation.setTypeText(type == null ? null : type.getPresentableText());
	}

	@Override
	public PsiType getType()
	{
		return myExpression.getType();
	}

	@Override
	public boolean equals(final Object o)
	{
		return o instanceof ExpressionLookupItem && myLookupString.equals(((ExpressionLookupItem) o).myLookupString);
	}

	@Override
	public int hashCode()
	{
		return myLookupString.hashCode();
	}

	@Nonnull
	@Override
	public String getLookupString()
	{
		return myLookupString;
	}

	@Override
	public Set<String> getAllLookupStrings()
	{
		return myAllLookupStrings;
	}
}