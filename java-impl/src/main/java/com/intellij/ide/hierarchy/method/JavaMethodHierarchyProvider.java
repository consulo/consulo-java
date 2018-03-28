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
package com.intellij.ide.hierarchy.method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.MethodHierarchyBrowserBase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author yole
 */
public class JavaMethodHierarchyProvider implements HierarchyProvider
{
	public PsiElement getTarget(@Nonnull final DataContext dataContext)
	{
		final PsiMethod method = getMethodImpl(dataContext);
		if(method != null && method.getContainingClass() != null && !method.hasModifierProperty(PsiModifier.PRIVATE) && !method.hasModifierProperty(PsiModifier.STATIC))
		{
			return method;
		}
		else
		{
			return null;
		}
	}

	@Nullable
	private static PsiMethod getMethodImpl(final DataContext dataContext)
	{
		final Project project = dataContext.getData(CommonDataKeys.PROJECT);
		if(project == null)
		{
			return null;
		}

		PsiElement element = dataContext.getData(LangDataKeys.PSI_ELEMENT);
		final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);

		if(method != null)
		{
			return method;
		}

		final Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
		if(editor == null)
		{
			return null;
		}

		final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
		if(psiFile == null)
		{
			return null;
		}

		PsiDocumentManager.getInstance(project).commitAllDocuments();

		final int offset = editor.getCaretModel().getOffset();
		if(offset < 1)
		{
			return null;
		}

		element = psiFile.findElementAt(offset);
		if(!(element instanceof PsiWhiteSpace))
		{
			return null;
		}

		element = psiFile.findElementAt(offset - 1);
		if(!(element instanceof PsiJavaToken) || ((PsiJavaToken) element).getTokenType() != JavaTokenType.SEMICOLON)
		{
			return null;
		}

		return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
	}

	@Nonnull
	public HierarchyBrowser createHierarchyBrowser(final PsiElement target)
	{
		return new MethodHierarchyBrowser(target.getProject(), (PsiMethod) target);
	}

	public void browserActivated(@Nonnull final HierarchyBrowser hierarchyBrowser)
	{
		((MethodHierarchyBrowser) hierarchyBrowser).changeView(MethodHierarchyBrowserBase.METHOD_TYPE);
	}
}
