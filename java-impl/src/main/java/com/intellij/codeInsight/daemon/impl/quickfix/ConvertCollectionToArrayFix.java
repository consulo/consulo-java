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
package com.intellij.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import consulo.java.language.module.util.JavaClassNames;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiArrayType;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.java.language.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.java.analysis.impl.JavaQuickFixBundle;

/**
 * @author Pavel.Dolgov
 */
public class ConvertCollectionToArrayFix implements IntentionAction
{
	private final PsiExpression myCollectionExpression;
	private final String myNewArrayText;

	public ConvertCollectionToArrayFix(@Nonnull PsiExpression collectionExpression, @Nonnull PsiArrayType arrayType)
	{
		myCollectionExpression = collectionExpression;

		PsiType componentType = arrayType.getComponentType();
		myNewArrayText = componentType.equalsToText(JavaClassNames.JAVA_LANG_OBJECT) ? "" : "new " + getArrayTypeText(componentType);
	}

	@Nls
	@Nonnull
	@Override
	public String getText()
	{
		return JavaQuickFixBundle.message("collection.to.array.text", myNewArrayText);
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("collection.to.array.family.name");
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		return myCollectionExpression.isValid() && PsiManager.getInstance(project).isInProject(myCollectionExpression);
	}

	@Override
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
	{
		PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
		String replacement = ParenthesesUtils.getText(myCollectionExpression, ParenthesesUtils.POSTFIX_PRECEDENCE) + ".toArray(" + myNewArrayText + ")";
		myCollectionExpression.replace(factory.createExpressionFromText(replacement, myCollectionExpression));
	}

	@Override
	public boolean startInWriteAction()
	{
		return true;
	}

	@Nonnull
	private static String getArrayTypeText(PsiType componentType)
	{
		if(componentType instanceof PsiArrayType)
		{
			return getArrayTypeText(((PsiArrayType) componentType).getComponentType()) + "[]";
		}
		if(componentType instanceof PsiClassType)
		{
			return ((PsiClassType) componentType).rawType().getCanonicalText() + "[0]";
		}
		return componentType.getCanonicalText() + "[0]";
	}
}
