/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.analysis.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.concurrent.AsyncResult;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class IntroduceVariableFix extends InspectionGadgetsFix
{

	private final boolean myMayChangeSemantics;

	public IntroduceVariableFix(boolean mayChangeSemantics)
	{
		myMayChangeSemantics = mayChangeSemantics;
	}

	@Nonnull
	@Override
	public String getName()
	{
		return myMayChangeSemantics
			? InspectionGadgetsLocalize.introduceVariableMayChangeSemanticsQuickfix().get()
			: InspectionGadgetsLocalize.introduceVariableQuickfix().get();
	}

	@Nullable
	public PsiExpression getExpressionToExtract(PsiElement element)
	{
		return PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
	}

	@Override
	protected void doFix(final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException
	{
		final PsiExpression expression = getExpressionToExtract(descriptor.getPsiElement());
		if(expression == null)
		{
			return;
		}
		final RefactoringActionHandler handler = JavaRefactoringActionHandlerFactory.getInstance().createIntroduceVariableHandler();
		final AsyncResult<DataContext> dataContextContainer = DataManager.getInstance().getDataContextFromFocus();
		dataContextContainer.doWhenDone(dataContext -> {
			handler.invoke(project, new PsiElement[]{expression}, dataContext);
		});
	}
}
