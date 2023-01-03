/*
 * Copyright 2013-2017 consulo.io
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
package com.intellij.java.analysis.impl.codeInspection.dataFlow.fix;

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.analysis.impl.refactoring.extractMethod.ExtractMethodUtil;

/**
 * @author peter
 */
public class ReplaceWithConstantValueFix implements LocalQuickFix
{
	private final String myPresentableName;
	private final String myReplacementText;

	public ReplaceWithConstantValueFix(String presentableName, String replacementText)
	{
		myPresentableName = presentableName;
		myReplacementText = replacementText;
	}

	@Nonnull
	@Override
	public String getName()
	{
		return "Replace with '" + myPresentableName + "'";
	}

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return "Replace with constant value";
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		PsiElement problemElement = descriptor.getPsiElement();
		if(problemElement == null)
		{
			return;
		}

		PsiMethodCallExpression call = problemElement.getParent() instanceof PsiExpressionList && problemElement.getParent().getParent() instanceof PsiMethodCallExpression ?
				(PsiMethodCallExpression) problemElement.getParent().getParent() : null;
		PsiMethod targetMethod = call == null ? null : call.resolveMethod();

		JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
		problemElement.replace(facade.getElementFactory().createExpressionFromText(myReplacementText, null));

		if(targetMethod != null)
		{
			ExtractMethodUtil.addCastsToEnsureResolveTarget(targetMethod, call);
		}
	}
}
