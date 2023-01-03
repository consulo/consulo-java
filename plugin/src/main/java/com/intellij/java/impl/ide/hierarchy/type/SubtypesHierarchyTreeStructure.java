/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import consulo.project.Project;
import consulo.java.language.module.util.JavaClassNames;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import com.intellij.java.language.psi.PsiModifier;
import consulo.content.scope.SearchScope;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import consulo.util.collection.ArrayUtil;
import consulo.application.util.function.Processor;

public class SubtypesHierarchyTreeStructure extends HierarchyTreeStructure
{
	private final String myCurrentScopeType;

	protected SubtypesHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor descriptor, String currentScopeType)
	{
		super(project, descriptor);
		myCurrentScopeType = currentScopeType;
	}

	public SubtypesHierarchyTreeStructure(Project project, PsiClass psiClass, String currentScopeType)
	{
		super(project, new TypeHierarchyNodeDescriptor(project, null, psiClass, true));
		myCurrentScopeType = currentScopeType;
	}

	@Override
	@Nonnull
	protected final Object[] buildChildren(@Nonnull final HierarchyNodeDescriptor descriptor)
	{
		final Object element = ((TypeHierarchyNodeDescriptor) descriptor).getPsiClass();
		if(!(element instanceof PsiClass))
		{
			return ArrayUtil.EMPTY_OBJECT_ARRAY;
		}
		final PsiClass psiClass = (PsiClass) element;
		if(JavaClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName()))
		{
			return new Object[]{IdeBundle.message("node.hierarchy.java.lang.object")};
		}
		if(psiClass instanceof PsiAnonymousClass)
		{
			return ArrayUtil.EMPTY_OBJECT_ARRAY;
		}
		if(psiClass.hasModifierProperty(PsiModifier.FINAL))
		{
			return ArrayUtil.EMPTY_OBJECT_ARRAY;
		}
		final SearchScope searchScope = psiClass.getUseScope().intersectWith(getSearchScope(myCurrentScopeType, psiClass));
		final List<PsiClass> classes = new ArrayList<PsiClass>(ClassInheritorsSearch.search(psiClass, searchScope, false).findAll());
		final List<HierarchyNodeDescriptor> descriptors = new ArrayList<HierarchyNodeDescriptor>(classes.size());
		for(PsiClass aClass : classes)
		{
			descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, aClass, false));
		}
		FunctionalExpressionSearch.search(psiClass, searchScope).forEach(new Processor<PsiFunctionalExpression>()
		{
			@Override
			public boolean process(PsiFunctionalExpression expression)
			{
				descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, expression, false));
				return true;
			}
		});
		return descriptors.toArray(new HierarchyNodeDescriptor[descriptors.size()]);
	}
}
