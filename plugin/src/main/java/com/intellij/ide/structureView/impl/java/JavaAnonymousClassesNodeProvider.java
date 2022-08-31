// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.AnonymousElementProvider;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.PropertyOwner;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import consulo.java.analysis.codeInsight.JavaCodeInsightBundle;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassesNodeProvider implements FileStructureNodeProvider<JavaAnonymousClassTreeElement>, PropertyOwner, DumbAware
{
	public static final String ID = "SHOW_ANONYMOUS";
	public static final String JAVA_ANONYMOUS_PROPERTY_NAME = "java.anonymous.provider";

	@Nonnull
	@Override
	public Collection<JavaAnonymousClassTreeElement> provideNodes(@Nonnull TreeElement node)
	{
		if(node instanceof PsiMethodTreeElement || node instanceof PsiFieldTreeElement || node instanceof ClassInitializerTreeElement)
		{
			final PsiElement el = ((PsiTreeElementBase) node).getElement();
			if(el != null)
			{
				for(AnonymousElementProvider provider : AnonymousElementProvider.EP_NAME.getExtensionList())
				{
					final PsiElement[] elements = provider.getAnonymousElements(el);
					if(elements.length > 0)
					{
						List<JavaAnonymousClassTreeElement> result = new ArrayList<>(elements.length);
						for(PsiElement element : elements)
						{
							result.add(new JavaAnonymousClassTreeElement((PsiAnonymousClass) element));
						}
						return result;
					}
				}
			}
		}
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public String getCheckBoxText()
	{
		return JavaCodeInsightBundle.message("file.structure.toggle.show.anonymous.classes");
	}

	@Override
	public Shortcut[] getShortcut()
	{
		return new Shortcut[]{KeyboardShortcut.fromString(SystemInfo.isMac ? "meta I" : "control I")};
	}

	@Nonnull
	@Override
	public ActionPresentation getPresentation()
	{
		return new ActionPresentationData(getCheckBoxText(), null, AllIcons.Nodes.AnonymousClass);
	}

	@Nonnull
	@Override
	public String getName()
	{
		return ID;
	}

	@Nonnull
	@Override
	public String getPropertyName()
	{
		return JAVA_ANONYMOUS_PROPERTY_NAME;
	}
}
