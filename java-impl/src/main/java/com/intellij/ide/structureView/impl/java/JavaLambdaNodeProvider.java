// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.PropertyOwner;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.psi.SyntaxTraverser;
import consulo.java.analysis.codeInsight.JavaCodeInsightBundle;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class JavaLambdaNodeProvider implements FileStructureNodeProvider<JavaLambdaTreeElement>, PropertyOwner, DumbAware
{
	public static final String ID = "SHOW_LAMBDA";
	public static final String JAVA_LAMBDA_PROPERTY_NAME = "java.lambda.provider";

	@Nonnull
	@Override
	public List<JavaLambdaTreeElement> provideNodes(@Nonnull TreeElement node)
	{
		if(!(node instanceof PsiTreeElementBase))
		{
			return Collections.emptyList();
		}
		PsiElement element = ((PsiTreeElementBase) node).getElement();
		return SyntaxTraverser.psiTraverser(element)
				.expand(o -> o == element || !(o instanceof PsiMember || o instanceof PsiLambdaExpression))
				.filter(PsiLambdaExpression.class)
				.filter(o -> o != element)
				.map(JavaLambdaTreeElement::new)
				.toList();
	}

	@Nonnull
	@Override
	public String getCheckBoxText()
	{
		return JavaCodeInsightBundle.message("file.structure.toggle.show.collapse.show.lambdas");
	}

	@Nonnull
	@Override
	public Shortcut[] getShortcut()
	{
		return new Shortcut[]{KeyboardShortcut.fromString(SystemInfo.isMac ? "meta L" : "control L")};
	}

	@Nonnull
	@Override
	public ActionPresentation getPresentation()
	{
		return new ActionPresentationData(getCheckBoxText(), null, AllIcons.Nodes.Lambda);
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
		return JAVA_LAMBDA_PROPERTY_NAME;
	}
}
