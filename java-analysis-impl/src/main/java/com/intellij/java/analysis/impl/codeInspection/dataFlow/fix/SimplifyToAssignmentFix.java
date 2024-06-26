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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
public class SimplifyToAssignmentFix implements LocalQuickFix
{
	@Nonnull
	@Override
	public String getName()
	{
		return InspectionLocalize.inspectionDataFlowSimplifyToAssignmentQuickfixName().get();
	}

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return InspectionLocalize.inspectionDataFlowSimplifyBooleanExpressionQuickfix().get();
	}

	@Override
	@RequiredReadAction
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement psiElement = descriptor.getPsiElement();
		if (psiElement == null)
		{
			return;
		}

		final PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(psiElement, PsiAssignmentExpression.class);
		if (assignmentExpression == null)
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
