// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar;

import com.google.common.collect.ImmutableList;
import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider;
import com.intellij.ide.structureView.impl.java.JavaLambdaNodeProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.usageView.UsageViewShortNameLocation;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.JavaBundle;
import consulo.psi.PsiPackage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import static com.intellij.psi.util.PsiFormatUtilBase.*;

/**
 * @author anna
 */
public class JavaNavBarExtension extends StructureAwareNavBarModelExtension
{
	private final List<NodeProvider<?>> myNodeProviders = ImmutableList.of(new JavaLambdaNodeProvider(), new JavaAnonymousClassesNodeProvider());

	@Nullable
	@Override
	@RequiredReadAction
	public String getPresentableText(Object object)
	{
		return getPresentableText(object, false);
	}

	@Override
	@RequiredReadAction
	public String getPresentableText(final Object object, boolean forPopup)
	{
		if(object instanceof PsiMember)
		{
			if(forPopup && object instanceof PsiMethod)
			{
				return PsiFormatUtil.formatMethod((PsiMethod) object,
						PsiSubstitutor.EMPTY,
						SHOW_NAME | TYPE_AFTER | SHOW_PARAMETERS,
						SHOW_TYPE);
			}
			return ElementDescriptionUtil.getElementDescription((PsiElement) object, UsageViewShortNameLocation.INSTANCE);
		}
		else if(object instanceof PsiJavaPackage)
		{
			final String name = ((PsiJavaPackage) object).getName();
			return name != null ? name : JavaBundle.message("dependencies.tree.node.default.package.abbreviation");
		}
		else if(object instanceof PsiDirectory && JrtFileSystem.isRoot(((PsiDirectory) object).getVirtualFile()))
		{
			return JavaBundle.message("jrt.node.short");
		}
		else if(object instanceof PsiLambdaExpression)
		{
			return "Lambda";
		}
		return null;
	}

	@RequiredReadAction
	@Override
	public PsiElement getParent(@Nonnull final PsiElement psiElement)
	{
		if(psiElement instanceof PsiPackage)
		{
			final PsiPackage parentPackage = ((PsiPackage) psiElement).getParentPackage();
			if(parentPackage != null && parentPackage.getQualifiedName().length() > 0)
			{
				return parentPackage;
			}
		}
		return super.getParent(psiElement);
	}

	@Nullable
	@Override
	public PsiElement adjustElement(@Nonnull final PsiElement psiElement)
	{
		final ProjectFileIndex index = ProjectRootManager.getInstance(psiElement.getProject()).getFileIndex();
		final PsiFile containingFile = psiElement.getContainingFile();
		if(containingFile != null)
		{
			final VirtualFile file = containingFile.getVirtualFile();
			if(file != null && (index.isInSourceContent(file) || index.isInLibraryClasses(file) || index.isInLibrary(file)))
			{
				if(psiElement instanceof PsiJavaFile)
				{
					final PsiJavaFile psiJavaFile = (PsiJavaFile) psiElement;
					if(psiJavaFile.getViewProvider().getBaseLanguage() == JavaLanguage.INSTANCE)
					{
						final PsiClass[] psiClasses = psiJavaFile.getClasses();
						if(psiClasses.length == 1)
						{
							return psiClasses[0];
						}
					}
				}
				if(!UISettings.getInstance().getShowMembersInNavigationBar() && psiElement instanceof PsiClass)
				{
					return psiElement;
				}
			}
			if(!UISettings.getInstance().getShowMembersInNavigationBar())
			{
				return containingFile;
			}
		}
		return psiElement;
	}

	@Nonnull
	@Override
	protected Language getLanguage()
	{
		return JavaLanguage.INSTANCE;
	}

	@Nonnull
	@Override
	protected List<NodeProvider<?>> getApplicableNodeProviders()
	{
		return myNodeProviders;
	}

	@Override
	protected boolean acceptParentFromModel(@Nullable PsiElement psiElement)
	{
		if(psiElement instanceof PsiJavaFile)
		{
			return ((PsiJavaFile) psiElement).getClasses().length > 1;
		}
		return true;
	}
}
