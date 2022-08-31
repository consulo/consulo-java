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
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author peter
 */
public class SimplifyToAssignmentFix implements LocalQuickFix
{
	@Nonnull
	@Override
	public String getName()
	{
		return InspectionsBundle.message("inspection.data.flow.simplify.to.assignment.quickfix.name");
	}

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return InspectionsBundle.message("inspection.data.flow.simplify.boolean.expression.quickfix");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement psiElement = descriptor.getPsiElement();
		if(psiElement == null)
		{
			return;
		}

		final PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(psiElement, PsiAssignmentExpression.class);
		if(assignmentExpression == null)
		{
			return;
		}

		final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
		final String lExpressionText = assignmentExpression.getLExpression().getText();
		final PsiExpression rExpression = assignmentExpression.getRExpression();
		final String rExpressionText = rExpression != null ? rExpression.getText() : "";
		assignmentExpression.replace(factory.createExpressionFromText(lExpressionText + " = " + rExpressionText, psiElement));
	}
}
