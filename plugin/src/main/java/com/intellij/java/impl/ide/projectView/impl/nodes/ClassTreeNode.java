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
package com.intellij.java.impl.ide.projectView.impl.nodes;

import com.intellij.java.impl.ide.projectView.PsiClassChildrenSource;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.application.dumb.IndexNotReadyException;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ui.ex.tree.PresentationData;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;

public class ClassTreeNode extends BasePsiMemberNode<PsiClass>
{
	public ClassTreeNode(Project project, PsiClass value, ViewSettings viewSettings)
	{
		super(project, value, viewSettings);
	}

	@Override
	public Collection<AbstractTreeNode> getChildrenImpl()
	{
		PsiClass parent = getValue();
		final ArrayList<AbstractTreeNode> treeNodes = new ArrayList<>();

		if(getSettings().isShowMembers())
		{
			ArrayList<PsiElement> result = new ArrayList<>();
			try
			{
				PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(parent, result);
			}
			catch(IndexNotReadyException ignore)
			{
			}
			for(PsiElement psiElement : result)
			{
				if(!psiElement.isPhysical())
				{
					continue;
				}

				if(psiElement instanceof PsiClass)
				{
					treeNodes.add(new ClassTreeNode(getProject(), (PsiClass) psiElement, getSettings()));
				}
				else if(psiElement instanceof PsiMethod)
				{
					treeNodes.add(new PsiMethodNode(getProject(), (PsiMethod) psiElement, getSettings()));
				}
				else if(psiElement instanceof PsiField)
				{
					treeNodes.add(new PsiFieldNode(getProject(), (PsiField) psiElement, getSettings()));
				}
			}
		}
		return treeNodes;
	}

	@Override
	public boolean isAlwaysLeaf()
	{
		return !getSettings().isShowMembers();
	}

	@Override
	public void updateImpl(PresentationData data)
	{
		final PsiClass aClass = getValue();
		if(aClass != null)
		{
			if(aClass.hasTypeParameters())
			{
				StringBuilder builder = new StringBuilder();
				builder.append(aClass.getName());
				PsiFormatUtil.formatTypeParameters(aClass, builder, PsiFormatUtilBase.SHOW_TYPE_PARAMETER_EXTENDS);
				data.setPresentableText(builder.toString());
			}
			else
			{
				data.setPresentableText(aClass.getName());
			}
		}
	}

	public boolean isTopLevel()
	{
		return getValue() != null && getValue().getParent() instanceof PsiFile;
	}


	@Override
	public boolean expandOnDoubleClick()
	{
		return false;
	}

	public PsiClass getPsiClass()
	{
		return getValue();
	}

	@Override
	public boolean isAlwaysExpand()
	{
		return getParentValue() instanceof PsiFile;
	}

	@Override
	public int getWeight()
	{
		return 20;
	}

	@Override
	public String getTitle()
	{
		final PsiClass psiClass = getValue();
		if(psiClass != null && psiClass.isValid())
		{
			return psiClass.getQualifiedName();
		}
		return super.getTitle();
	}

	@Override
	protected boolean isMarkReadOnly()
	{
		return true;
	}

	@Override
	public int getTypeSortWeight(final boolean sortByType)
	{
		return sortByType ? 5 : 0;
	}

	@Override
	public Comparable getTypeSortKey()
	{
		return new ClassNameSortKey();
	}

	public static int getClassPosition(final PsiClass aClass)
	{
		if(aClass == null || !aClass.isValid())
		{
			return 0;
		}
		try
		{
			int pos = 0;
			if(aClass.isInterface())
			{
				pos = 1;
			}
			else if(aClass.isEnum())
			{
				pos = 2;
			}
			else if(aClass.hasModifierProperty(PsiModifier.ABSTRACT))
			{
				pos = -1;
			}
			//abstract class before concrete
	  /*if (pos == ElementPresentationUtil.CLASS_KIND_CLASS || pos == ElementPresentationUtil.CLASS_KIND_EXCEPTION) {
        boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isInterface();
        if (isAbstract) {
          pos --;
        }
      } */
			return pos;
		}
		catch(IndexNotReadyException e)
		{
			return 0;
		}
	}

	private class ClassNameSortKey implements Comparable
	{
		@Override
		public int compareTo(final Object o)
		{
			if(!(o instanceof ClassNameSortKey))
			{
				return 0;
			}
			ClassNameSortKey rhs = (ClassNameSortKey) o;
			return getPosition() - rhs.getPosition();
		}

		int getPosition()
		{
			return getClassPosition(getValue());
		}
	}

	@Override
	public boolean shouldDrillDownOnEmptyElement()
	{
		return true;
	}

	@Override
	public boolean canRepresent(final Object element)
	{
		if(!isValid())
		{
			return false;
		}

		return super.canRepresent(element) || canRepresent(getValue(), element);
	}

	private boolean canRepresent(final PsiClass psiClass, final Object element)
	{
		if(psiClass == null || !psiClass.isValid() || element == null)
		{
			return false;
		}

		final PsiFile parentFile = parentFileOf(psiClass);
		if(parentFile != null && (parentFile == element || element.equals(parentFile.getVirtualFile())))
		{
			return true;
		}

		if(!getSettings().isShowMembers())
		{
			if(element instanceof PsiElement && ((PsiElement) element).isValid())
			{
				PsiFile elementFile = ((PsiElement) element).getContainingFile();
				if(elementFile != null && parentFile != null)
				{
					return elementFile.equals(parentFile);
				}
			}
		}

		return false;
	}

	@Nullable
	private static PsiFile parentFileOf(final PsiClass psiClass)
	{
		return psiClass.getContainingClass() == null ? psiClass.getContainingFile() : null;
	}
}
