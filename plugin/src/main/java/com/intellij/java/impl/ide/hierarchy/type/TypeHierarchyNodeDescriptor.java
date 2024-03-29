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
package com.intellij.java.impl.ide.hierarchy.type;

import java.awt.Font;

import consulo.application.AllIcons;
import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.java.impl.ide.hierarchy.JavaHierarchyUtil;
import consulo.colorScheme.TextAttributes;
import consulo.project.Project;
import consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance;
import consulo.util.lang.Comparing;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import consulo.ui.image.ImageEffects;

public final class TypeHierarchyNodeDescriptor extends HierarchyNodeDescriptor
{
	public TypeHierarchyNodeDescriptor(final Project project, final HierarchyNodeDescriptor parentDescriptor, final PsiElement classOrFunctionalExpression, final boolean isBase)
	{
		super(project, parentDescriptor, classOrFunctionalExpression, isBase);
	}

	public final PsiElement getPsiClass()
	{
		return getPsiElement();
	}

	public final boolean update()
	{
		boolean changes = super.update();

		if(getPsiElement() == null)
		{
			final String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
			if(!myHighlightedText.getText().startsWith(invalidPrefix))
			{
				myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
			}
			return true;
		}

		if(changes && myIsBase)
		{
			setIcon(ImageEffects.appendRight(AllIcons.Hierarchy.Base, getIcon()));
		}

		final PsiElement psiElement = getPsiClass();

		final CompositeAppearance oldText = myHighlightedText;

		myHighlightedText = new CompositeAppearance();

		TextAttributes classNameAttributes = null;
		if(myColor != null)
		{
			classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
		}
		if(psiElement instanceof PsiClass)
		{
			myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass) psiElement, false), classNameAttributes);
			myHighlightedText.getEnding().addText(" (" + JavaHierarchyUtil.getPackageName((PsiClass) psiElement) + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
		}
		else if(psiElement instanceof PsiFunctionalExpression)
		{
			myHighlightedText.getEnding().addText(ClassPresentationUtil.getFunctionalExpressionPresentation(((PsiFunctionalExpression) psiElement), false));
		}
		myName = myHighlightedText.getText();

		if(!Comparing.equal(myHighlightedText, oldText))
		{
			changes = true;
		}
		return changes;
	}

}
