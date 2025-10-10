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

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

/**
 * @author peter
 */
public class ReplaceWithObjectsEqualsFix implements LocalQuickFix
{
	private final String myQualifierText;
	private final String myReplacementText;

	private ReplaceWithObjectsEqualsFix(String qualifierText, String replacementText)
	{
		myQualifierText = qualifierText;
		myReplacementText = replacementText;
	}

	@Nls
	@Nonnull
	@Override
	public LocalizeValue getName()
	{
		return LocalizeValue.localizeTODO("Replace '" + myQualifierText + ".equals(...)' with 'Objects.equals(" + myReplacementText + ", ...)'");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
		if(call == null)
		{
			return;
		}

		PsiExpression[] args = call.getArgumentList().getExpressions();
		if(args.length != 1)
		{
			return;
		}

		String replacementText = "java.util.Objects.equals(" + myReplacementText + ", " + args[0].getText() + ")";
		PsiElement replaced = call.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacementText, call));
		JavaCodeStyleManager.getInstance(project).shortenClassReferences(((PsiMethodCallExpression) replaced).getMethodExpression());
	}

	@Nullable
	public static ReplaceWithObjectsEqualsFix createFix(@Nonnull PsiMethodCallExpression call, @Nonnull PsiReferenceExpression methodExpression)
	{
		if(!"equals".equals(methodExpression.getReferenceName()) || call.getArgumentList().getExpressions().length != 1 || !PsiUtil.getLanguageLevel(call).isAtLeast(LanguageLevel.JDK_1_7))
		{
			return null;
		}

		PsiExpression qualifier = methodExpression.getQualifierExpression();
		PsiExpression noParens = PsiUtil.skipParenthesizedExprDown(qualifier);
		if(noParens == null)
		{
			return null;
		}

		PsiMethod method = call.resolveMethod();
		if(method != null && method.getParameterList().getParametersCount() == 1
			&& method.getParameterList().getParameters()[0].getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT))
		{
			return new ReplaceWithObjectsEqualsFix(qualifier.getText(), noParens.getText());
		}
		return null;
	}
}
