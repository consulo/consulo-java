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
package com.intellij.codeInspection.dataFlow.fix;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;

import javax.annotation.Nullable;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import consulo.java.module.util.JavaClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

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
	public String getName()
	{
		return "Replace '" + myQualifierText + ".equals(...)' with 'Objects.equals(" + myReplacementText + ", ...)'";
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return "Replace '.equals()' with 'Objects.equals()'";
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
		if(method != null && method.getParameterList().getParametersCount() == 1 && method.getParameterList().getParameters()[0].getType().equalsToText(JavaClassNames.JAVA_LANG_OBJECT))
		{
			return new ReplaceWithObjectsEqualsFix(qualifier.getText(), noParens.getText());
		}
		return null;
	}
}
