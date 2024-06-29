/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public class RemoveInitializerFix implements LocalQuickFix
{
	private static final Logger LOG = Logger.getInstance(RemoveInitializerFix.class);

	@Override
	@Nonnull
	public String getName()
	{
		return InspectionLocalize.inspectionUnusedAssignmentRemoveQuickfix().get();
	}

	@Override
	@RequiredWriteAction
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement psiInitializer = descriptor.getPsiElement();
		if (!(psiInitializer instanceof PsiExpression))
		{
			return;
		}
		if (!(psiInitializer.getParent() instanceof PsiVariable))
		{
			return;
		}

		final PsiVariable variable = (PsiVariable) psiInitializer.getParent();
		sideEffectAwareRemove(project, psiInitializer, psiInitializer, variable);
	}

	@RequiredReadAction
	protected void sideEffectAwareRemove(Project project, PsiElement psiInitializer, PsiElement elementToDelete, PsiVariable variable)
	{
		if (!FileModificationService.getInstance().prepareFileForWrite(elementToDelete.getContainingFile()))
		{
			return;
		}

		final PsiElement declaration = variable.getParent();
		final List<PsiElement> sideEffects = new ArrayList<>();
		boolean hasSideEffects = RemoveUnusedVariableUtil.checkSideEffects(psiInitializer, variable, sideEffects);
		int res;
		if (hasSideEffects)
		{
			hasSideEffects = PsiUtil.isStatement(psiInitializer);
			res = RemoveUnusedVariableFix.showSideEffectsWarning(
				sideEffects,
				variable,
				FileEditorManager.getInstance(project).getSelectedTextEditor(),
				hasSideEffects,
				sideEffects.get(0).getText(),
				variable.getTypeElement().getText() + " " + variable.getName() + ";<br>" +
					PsiExpressionTrimRenderer.render((PsiExpression) psiInitializer)
			);
		}
		else
		{
			res = RemoveUnusedVariableUtil.DELETE_ALL;
		}

		if (res == RemoveUnusedVariableUtil.DELETE_ALL)
		{
			elementToDelete.delete();
		}
		else if (res == RemoveUnusedVariableUtil.MAKE_STATEMENT)
		{
			final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
			final PsiStatement statementFromText = factory.createStatementFromText(psiInitializer.getText() + ";", null);
			final PsiElement parent = elementToDelete.getParent();
			if (parent instanceof PsiExpressionStatement)
			{
				parent.replace(statementFromText);
			}
			else
			{
				declaration.getParent().addAfter(statementFromText, declaration);
				elementToDelete.delete();
			}
		}
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		return getName();
	}
}
