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

import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiInstanceOfExpression;

import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
public class RedundantInstanceofFix implements LocalQuickFix
{
	@Override
	@Nonnull
	public String getFamilyName()
	{
		return InspectionsBundle.message("inspection.data.flow.redundant.instanceof.quickfix");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement psiElement = descriptor.getPsiElement();
		if(psiElement instanceof PsiInstanceOfExpression)
		{
			PsiExpression compareToNull = JavaPsiFacade.getInstance(psiElement.getProject()).getElementFactory().
					createExpressionFromText(((PsiInstanceOfExpression) psiElement).getOperand().getText() + " != null", psiElement.getParent());
			psiElement.replace(compareToNull);
		}
	}
}
