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
package com.intellij.ide.hierarchy.type;

import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.swing.JPanel;
import javax.swing.JTree;

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import consulo.java.module.util.JavaClassNames;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

public class TypeHierarchyBrowser extends TypeHierarchyBrowserBase
{
	private static final Logger LOG = Logger.getInstance(TypeHierarchyBrowser.class);


	public TypeHierarchyBrowser(final Project project, final PsiClass psiClass)
	{
		super(project, psiClass);
	}

	@Override
	protected boolean isInterface(PsiElement psiElement)
	{
		return psiElement instanceof PsiClass && ((PsiClass) psiElement).isInterface();
	}

	@Override
	protected void createTrees(@Nonnull Map<String, JTree> trees)
	{
		createTreeAndSetupCommonActions(trees, IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
	}

	@Override
	protected void prependActions(DefaultActionGroup actionGroup)
	{
		super.prependActions(actionGroup);
		actionGroup.add(new ChangeScopeAction()
		{
			@Override
			protected boolean isEnabled()
			{
				return !Comparing.strEqual(myCurrentViewType, SUPERTYPES_HIERARCHY_TYPE);
			}
		});
	}

	@Override
	protected String getContentDisplayName(@Nonnull String typeName, @Nonnull PsiElement element)
	{
		return MessageFormat.format(typeName, ClassPresentationUtil.getNameForClass((PsiClass) element, false));
	}

	@Override
	protected PsiElement getElementFromDescriptor(@Nonnull HierarchyNodeDescriptor descriptor)
	{
		if(!(descriptor instanceof TypeHierarchyNodeDescriptor))
		{
			return null;
		}
		return ((TypeHierarchyNodeDescriptor) descriptor).getPsiClass();
	}

	@Override
	@javax.annotation.Nullable
	protected JPanel createLegendPanel()
	{
		return null;
	}

	@Override
	protected boolean isApplicableElement(@Nonnull final PsiElement element)
	{
		return element instanceof PsiClass;
	}

	@Override
	protected Comparator<NodeDescriptor> getComparator()
	{
		return JavaHierarchyUtil.getComparator(myProject);
	}

	@Override
	protected HierarchyTreeStructure createHierarchyTreeStructure(@Nonnull final String typeName, @Nonnull final PsiElement psiElement)
	{
		if(SUPERTYPES_HIERARCHY_TYPE.equals(typeName))
		{
			return new SupertypesHierarchyTreeStructure(myProject, (PsiClass) psiElement);
		}
		else if(SUBTYPES_HIERARCHY_TYPE.equals(typeName))
		{
			return new SubtypesHierarchyTreeStructure(myProject, (PsiClass) psiElement, getCurrentScopeType());
		}
		else if(TYPE_HIERARCHY_TYPE.equals(typeName))
		{
			return new TypeHierarchyTreeStructure(myProject, (PsiClass) psiElement, getCurrentScopeType());
		}
		else
		{
			LOG.error("unexpected type: " + typeName);
			return null;
		}
	}

	@Override
	protected boolean canBeDeleted(final PsiElement psiElement)
	{
		return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
	}

	@Override
	protected String getQualifiedName(final PsiElement psiElement)
	{
		if(psiElement instanceof PsiClass)
		{
			return ((PsiClass) psiElement).getQualifiedName();
		}
		return "";
	}

	public static class BaseOnThisTypeAction extends TypeHierarchyBrowserBase.BaseOnThisTypeAction
	{
		protected boolean isEnabled(@Nonnull final HierarchyBrowserBaseEx browser, @Nonnull final PsiElement psiElement)
		{
			return super.isEnabled(browser, psiElement) && !JavaClassNames.JAVA_LANG_OBJECT.equals(((PsiClass) psiElement).getQualifiedName());
		}
	}

	@Nonnull
	@Override
	protected TypeHierarchyBrowserBase.BaseOnThisTypeAction createBaseOnThisAction()
	{
		return new BaseOnThisTypeAction();
	}
}
