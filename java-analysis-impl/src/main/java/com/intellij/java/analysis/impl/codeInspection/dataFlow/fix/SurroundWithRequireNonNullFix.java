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
package com.intellij.java.analysis.impl.codeInspection.dataFlow.fix;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiExpression;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.java.analysis.impl.codeInsight.JavaInspectionsBundle;

public class SurroundWithRequireNonNullFix implements LocalQuickFix
{
	private final String myText;
	private final SmartPsiElementPointer<PsiExpression> myQualifierPointer;

	public SurroundWithRequireNonNullFix(@Nonnull PsiExpression expressionToSurround)
	{
		myText = expressionToSurround.getText();
		myQualifierPointer = SmartPointerManager.getInstance(expressionToSurround.getProject()).createSmartPsiElementPointer(expressionToSurround);
	}

	@Nls
	@Nonnull
	@Override
	public String getName()
	{
		return JavaInspectionsBundle.message("inspection.surround.requirenonnull.quickfix", myText);
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return JavaInspectionsBundle.message("inspection.surround.requirenonnull.quickfix", "");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		PsiExpression qualifier = myQualifierPointer.getElement();
		if(qualifier == null)
		{
			return;
		}
		PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText("java.util.Objects.requireNonNull(" + qualifier.getText() + ")", qualifier);
		JavaCodeStyleManager.getInstance(project).shortenClassReferences(qualifier.replace(replacement));
	}
}
