/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoUtil;
import consulo.logging.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import consulo.java.codeInsight.JavaInspectionsBundle;

/**
 * @author cdr
 */
public class AnnotateMethodFix implements LocalQuickFix
{
	private static final Logger LOG = Logger.getInstance(AnnotateMethodFix.class);

	private final String myAnnotation;
	private final String[] myAnnotationsToRemove;

	public AnnotateMethodFix(@Nonnull String fqn, @Nonnull String... annotationsToRemove)
	{
		myAnnotation = fqn;
		myAnnotationsToRemove = annotationsToRemove;
		LOG.assertTrue(annotateSelf() || annotateOverriddenMethods(), "annotate method quick fix should not do nothing");
	}

	@Override
	@Nonnull
	public String getName()
	{
		return getFamilyName() + " " + getPreposition() + " \'@" + ClassUtil.extractClassName(myAnnotation) + "\'";
	}

	@Nonnull
	protected String getPreposition()
	{
		return "with";
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		if(annotateSelf())
		{
			if(annotateOverriddenMethods())
			{
				return JavaInspectionsBundle.message("inspection.annotate.overridden.method.and.self.quickfix.family.name");
			}
			else
			{
				return JavaInspectionsBundle.message("inspection.annotate.method.quickfix.family.name");
			}
		}
		else
		{
			return JavaInspectionsBundle.message("inspection.annotate.overridden.method.quickfix.family.name");
		}
	}

	@Override
	public boolean startInWriteAction()
	{
		return false;
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement psiElement = descriptor.getPsiElement();

		PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
		if(method == null)
		{
			return;
		}
		final List<PsiMethod> toAnnotate = new ArrayList<>();
		if(annotateSelf())
		{
			toAnnotate.add(method);
		}

		if(annotateOverriddenMethods() && !ProgressManager.getInstance().runProcessWithProgressSynchronously(() ->
		{
			PsiMethod[] methods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
			for(PsiMethod psiMethod : methods)
			{
				ReadAction.run(() ->
				{
					if(psiMethod.isPhysical() && psiMethod.getManager().isInProject(psiMethod) && AnnotationUtil.isAnnotatingApplicable(psiMethod, myAnnotation) && !AnnotationUtil.isAnnotated
							(psiMethod, myAnnotation, false, false, true))
					{
						toAnnotate.add(psiMethod);
					}
				});
			}
		}, "Searching for Overriding Methods", true, project))
		{
			return;
		}

		FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
		for(PsiMethod psiMethod : toAnnotate)
		{
			annotateMethod(psiMethod);
		}
		UndoUtil.markPsiFileForUndo(method.getContainingFile());
	}

	protected boolean annotateOverriddenMethods()
	{
		return false;
	}

	protected boolean annotateSelf()
	{
		return true;
	}

	private void annotateMethod(@Nonnull PsiMethod method)
	{
		AddAnnotationPsiFix fix = new AddAnnotationPsiFix(myAnnotation, method, PsiNameValuePair.EMPTY_ARRAY, myAnnotationsToRemove);
		fix.invoke(method.getProject(), method.getContainingFile(), method, method);
	}
}
