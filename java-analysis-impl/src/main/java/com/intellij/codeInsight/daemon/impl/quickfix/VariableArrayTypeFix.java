/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.ide.TypePresentationService;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import consulo.java.JavaQuickFixBundle;

public class VariableArrayTypeFix extends LocalQuickFixOnPsiElement
{
	@Nonnull
	private final PsiArrayType myTargetType;
	private final String myName;
	private final String myFamilyName;

	private VariableArrayTypeFix(@Nonnull PsiArrayInitializerExpression initializer, @Nonnull PsiArrayType arrayType, @Nonnull PsiVariable variable)
	{
		super(initializer);
		myTargetType = arrayType;
		PsiExpression myNewExpression = getNewExpressionLocal(initializer);
		myName = myTargetType.equals(variable.getType()) && myNewExpression != null ? JavaQuickFixBundle.message("change.new.operator.type.text", getNewText(myNewExpression, initializer),
				myTargetType.getCanonicalText(), "") : JavaQuickFixBundle.message("fix.variable.type.text", formatType(variable), variable.getName(), myTargetType.getCanonicalText());
		myFamilyName = JavaQuickFixBundle.message(myTargetType.equals(variable.getType()) && myNewExpression != null ? "change.new.operator.type.family" : "fix.variable.type.family");
	}

	@Nullable
	public static VariableArrayTypeFix createFix(PsiArrayInitializerExpression initializer, @Nonnull PsiType componentType)
	{
		PsiArrayType arrayType = new PsiArrayType(componentType);
		PsiArrayInitializerExpression arrayInitializer = initializer;
		while(arrayInitializer.getParent() instanceof PsiArrayInitializerExpression)
		{
			arrayInitializer = (PsiArrayInitializerExpression) arrayInitializer.getParent();
			arrayType = new PsiArrayType(arrayType);
		}
		PsiVariable variable = getVariableLocal(arrayInitializer);
		if(variable == null)
		{
			return null;
		}
		return new VariableArrayTypeFix(arrayInitializer, arrayType, variable);
	}

	private static String formatType(@Nonnull PsiVariable variable)
	{
		FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(variable.getLanguage());
		final String type = provider.getType(variable);
		if(StringUtil.isNotEmpty(type))
		{
			return type;
		}

		return TypePresentationService.getInstance().getTypePresentableName(variable.getClass());
	}

	private static PsiArrayInitializerExpression getInitializer(PsiArrayInitializerExpression initializer)
	{
		PsiArrayInitializerExpression arrayInitializer = initializer;
		while(arrayInitializer.getParent() instanceof PsiArrayInitializerExpression)
		{
			arrayInitializer = (PsiArrayInitializerExpression) arrayInitializer.getParent();
		}

		return arrayInitializer;
	}

	private static PsiVariable getVariableLocal(@Nonnull PsiArrayInitializerExpression initializer)
	{
		PsiVariable variableLocal = null;

		final PsiElement parent = initializer.getParent();
		if(parent instanceof PsiVariable)
		{
			variableLocal = (PsiVariable) parent;
		}
		else if(parent instanceof PsiNewExpression)
		{
			PsiNewExpression newExpressionLocal = (PsiNewExpression) parent;
			final PsiElement newParent = newExpressionLocal.getParent();
			if(newParent instanceof PsiAssignmentExpression)
			{
				variableLocal = getFromAssignment((PsiAssignmentExpression) newParent);
			}
			else if(newParent instanceof PsiVariable)
			{
				variableLocal = (PsiVariable) newParent;
			}
		}
		else if(parent instanceof PsiAssignmentExpression)
		{
			variableLocal = getFromAssignment((PsiAssignmentExpression) parent);
		}
		return variableLocal;
	}

	private static PsiNewExpression getNewExpressionLocal(@Nonnull PsiArrayInitializerExpression initializer)
	{
		PsiNewExpression newExpressionLocal = null;

		final PsiElement parent = initializer.getParent();
		if(parent instanceof PsiVariable)
		{

		}
		else if(parent instanceof PsiNewExpression)
		{
			newExpressionLocal = (PsiNewExpression) parent;
		}

		return newExpressionLocal;
	}

	@Nullable
	private static PsiVariable getFromAssignment(final PsiAssignmentExpression assignment)
	{
		final PsiExpression reference = assignment.getLExpression();
		final PsiElement referencedElement = reference instanceof PsiReferenceExpression ? ((PsiReferenceExpression) reference).resolve() : null;
		return referencedElement != null && referencedElement instanceof PsiVariable ? (PsiVariable) referencedElement : null;
	}

	private static String getNewText(PsiElement myNewExpression, PsiArrayInitializerExpression myInitializer)
	{
		final String newText = myNewExpression.getText();
		final int initializerIdx = newText.indexOf(myInitializer.getText());
		if(initializerIdx != -1)
		{
			return newText.substring(0, initializerIdx).trim();
		}
		return newText;
	}

	@Nonnull
	@Override
	public String getText()
	{
		return myName;
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		return myFamilyName;
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
	{
		final PsiArrayInitializerExpression myInitializer = (PsiArrayInitializerExpression) startElement;
		final PsiVariable myVariable = getVariableLocal(myInitializer);

		return myVariable != null && myVariable.isValid() && myVariable.getManager().isInProject(myVariable) && myTargetType.isValid() && myInitializer.isValid();
	}

	@Override
	public void invoke(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
	{
		final PsiArrayInitializerExpression myInitializer = (PsiArrayInitializerExpression) startElement;
		final PsiVariable myVariable = getVariableLocal(myInitializer);
		if(myVariable == null)
		{
			return;
		}
		/**
		 only for the case when in same statement with initialization
		 */
		final PsiNewExpression myNewExpression = getNewExpressionLocal(myInitializer);

		if(!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile()))
		{
			return;
		}

		if(!myTargetType.equals(myVariable.getType()))
		{
			WriteAction.run(() -> fixVariableType(project, file, myVariable));
		}

		if(myNewExpression != null)
		{
			if(!FileModificationService.getInstance().prepareFileForWrite(file))
			{
				return;
			}

			WriteAction.run(() -> fixArrayInitializer(myInitializer, myNewExpression));
		}
	}

	private void fixVariableType(@Nonnull Project project, @Nonnull PsiFile file, PsiVariable myVariable)
	{
		myVariable.normalizeDeclaration();
		myVariable.getTypeElement().replace(JavaPsiFacade.getElementFactory(project).createTypeElement(myTargetType));
		JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);

		if(!myVariable.getContainingFile().equals(file))
		{
			UndoUtil.markPsiFileForUndo(myVariable.getContainingFile());
		}
	}

	private void fixArrayInitializer(PsiArrayInitializerExpression myInitializer, PsiNewExpression myNewExpression)
	{
		@NonNls String text = "new " + myTargetType.getCanonicalText() + "{}";
		final PsiNewExpression newExpression = (PsiNewExpression) JavaPsiFacade.getElementFactory(myNewExpression.getProject()).createExpressionFromText(text, myNewExpression.getParent());
		final PsiElement[] children = newExpression.getChildren();
		children[children.length - 1].replace(myInitializer);
		myNewExpression.replace(newExpression);
	}
}