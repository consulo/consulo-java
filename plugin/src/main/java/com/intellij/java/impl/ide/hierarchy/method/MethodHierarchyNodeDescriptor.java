/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.ide.hierarchy.method;

import com.intellij.java.impl.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.application.AllIcons;
import consulo.colorScheme.TextAttributes;
import consulo.component.util.Iconable;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance;
import consulo.ide.localize.IdeLocalize;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.util.lang.Comparing;

import java.awt.*;

public final class MethodHierarchyNodeDescriptor extends HierarchyNodeDescriptor
{
	private Image myRawIcon;
	private Image myStateIcon;
	private MethodHierarchyTreeStructure myTreeStructure;

	public MethodHierarchyNodeDescriptor(
		final Project project,
		final HierarchyNodeDescriptor parentDescriptor,
		final PsiElement aClass,
		final boolean isBase,
		final MethodHierarchyTreeStructure treeStructure
	) {
		super(project, parentDescriptor, aClass, isBase);
		myTreeStructure = treeStructure;
	}

	public final void setTreeStructure(final MethodHierarchyTreeStructure treeStructure)
	{
		myTreeStructure = treeStructure;
	}

	PsiMethod getMethod(final PsiClass aClass, final boolean checkBases)
	{
		return MethodHierarchyUtil.findBaseMethodInClass(myTreeStructure.getBaseMethod(), aClass, checkBases);
	}

	public final PsiElement getPsiClass()
	{
		return getPsiElement();
	}

	/**
	 * Element for OpenFileDescriptor
	 */
	public final PsiElement getTargetElement()
	{
		final PsiElement element = getPsiClass();
		if (!(element instanceof PsiClass))
		{
			return element;
		}
		final PsiClass aClass = (PsiClass) getPsiClass();
		if (!aClass.isValid())
		{
			return null;
		}
		final PsiMethod method = getMethod(aClass, false);
		if (method != null)
		{
			return method;
		}
		return aClass;
	}

	@Override
	@RequiredUIAccess
	public final boolean update()
	{
		int flags = Iconable.ICON_FLAG_VISIBILITY;
		if (isMarkReadOnly())
		{
			flags |= Iconable.ICON_FLAG_READ_STATUS;
		}

		boolean changes = super.update();

		final PsiElement aClass = getPsiClass();

		if (aClass == null)
		{
			final String invalidPrefix = IdeLocalize.nodeHierarchyInvalid().get();
			if (!myHighlightedText.getText().startsWith(invalidPrefix))
			{
				myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
			}
			return true;
		}

		final Image newRawIcon = IconDescriptorUpdaters.getIcon(aClass, flags);
		final Image newStateIcon = aClass instanceof PsiClass psiClass ? calculateState(psiClass) : AllIcons.Hierarchy.MethodDefined;

		if (changes || newRawIcon != myRawIcon || newStateIcon != myStateIcon)
		{
			changes = true;

			myRawIcon = newRawIcon;
			myStateIcon = newStateIcon;

			Image newIcon = myRawIcon;

			if (myIsBase)
			{
				newIcon = ImageEffects.appendRight(AllIcons.Hierarchy.Base, newIcon);
			}

			if (myStateIcon != null)
			{
				newIcon = ImageEffects.appendRight(myStateIcon, newIcon);
			}

			setIcon(newIcon);
		}

		final CompositeAppearance oldText = myHighlightedText;

		myHighlightedText = new CompositeAppearance();
		TextAttributes classNameAttributes = null;
		if (myColor != null)
		{
			classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
		}
		if (aClass instanceof PsiClass psiClass)
		{
			myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass(psiClass, false), classNameAttributes);
			myHighlightedText.getEnding().addText(
				"  (" + JavaHierarchyUtil.getPackageName(psiClass) + ")",
				HierarchyNodeDescriptor.getPackageNameAttributes()
			);
		}
		else if (aClass instanceof PsiFunctionalExpression functionalExpression)
		{
			myHighlightedText.getEnding().addText(ClassPresentationUtil.getFunctionalExpressionPresentation(functionalExpression, false));
		}
		myName = myHighlightedText.getText();

		if (!Comparing.equal(myHighlightedText, oldText))
		{
			changes = true;
		}
		return changes;
	}

	private Image calculateState(final PsiClass psiClass)
	{
		final PsiMethod method = getMethod(psiClass, false);
		if (method != null)
		{
			return method.hasModifierProperty(PsiModifier.ABSTRACT) ? null : AllIcons.Hierarchy.MethodDefined;
		}

		if (myTreeStructure.isSuperClassForBaseClass(psiClass))
		{
			return AllIcons.Hierarchy.MethodNotDefined;
		}

		final boolean isAbstractClass = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

		// was it implemented is in superclasses?
		final PsiMethod baseClassMethod = getMethod(psiClass, true);

		final boolean hasBaseImplementation = baseClassMethod != null && !baseClassMethod.hasModifierProperty(PsiModifier.ABSTRACT);

		return hasBaseImplementation || isAbstractClass ? AllIcons.Hierarchy.MethodNotDefined : AllIcons.Hierarchy.ShouldDefineMethod;
	}
}
