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

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.Nls;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import consulo.java.JavaQuickFixBundle;
import consulo.java.module.util.JavaClassNames;

/**
 * @author Dmitry Batkovich
 */
public class WrapObjectWithOptionalOfNullableFix extends MethodArgumentFix implements HighPriorityAction
{
	public static final ArgumentFixerActionFactory REGISTAR = new MyFixerActionFactory();

	protected WrapObjectWithOptionalOfNullableFix(final @Nonnull PsiExpressionList list, final int i, final @Nonnull PsiType toType, final @Nonnull ArgumentFixerActionFactory fixerActionFactory)
	{
		super(list, i, toType, fixerActionFactory);
	}

	@Nonnull
	@Override
	public String getText()
	{
		if(myArgList.getExpressions().length == 1)
		{
			return JavaQuickFixBundle.message("wrap.with.optional.single.parameter.text");
		}
		else
		{
			return JavaQuickFixBundle.message("wrap.with.optional.parameter.text", myIndex + 1);
		}
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		return PsiUtil.isLanguageLevel8OrHigher(file) && super.isAvailable(project, editor, file);
	}

	public static IntentionAction createFix(@Nullable PsiType type, @Nonnull PsiExpression expression)
	{
		class MyFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction
		{
			protected MyFix(@Nullable PsiElement element)
			{
				super(element);
			}

			@Nls
			@Nonnull
			@Override
			public String getFamilyName()
			{
				return JavaQuickFixBundle.message("wrap.with.optional.single.parameter.text");
			}

			@Override
			public void invoke(@Nonnull Project project,
					@Nonnull PsiFile file,
					@Nullable Editor editor,
					@Nonnull PsiElement startElement,
					@Nonnull PsiElement endElement)
			{
				startElement.replace(getModifiedExpression((PsiExpression) getStartElement()));
			}

			@Override
			public boolean isAvailable(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
			{
				return startElement.isValid() && startElement.getManager().isInProject(startElement) && PsiUtil.isLanguageLevel8OrHigher(startElement) && areConvertible(((PsiExpression)
						startElement).getType(), type);
			}

			@Nonnull
			@Override
			public String getText()
			{
				return getFamilyName();
			}
		}
		return new MyFix(expression);
	}

	public static class MyFixerActionFactory extends ArgumentFixerActionFactory
	{

		@Nullable
		@Override
		protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException
		{
			return getModifiedExpression(expression);
		}

		@Override
		public boolean areTypesConvertible(@Nonnull final PsiType exprType, @Nonnull final PsiType parameterType, @Nonnull final PsiElement context)
		{
			return parameterType.isConvertibleFrom(exprType) || areConvertible(exprType, parameterType);
		}

		@Override
		public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType)
		{
			return new WrapObjectWithOptionalOfNullableFix(list, i, toType, this);
		}
	}

	private static boolean areConvertible(@javax.annotation.Nullable PsiType exprType, @Nullable PsiType parameterType)
	{
		if(exprType == null || !exprType.isValid() || !(parameterType instanceof PsiClassType) || !parameterType.isValid())
		{
			return false;
		}
		final PsiClassType.ClassResolveResult resolve = ((PsiClassType) parameterType).resolveGenerics();
		final PsiClass resolvedClass = resolve.getElement();
		if(resolvedClass == null || !JavaClassNames.JAVA_UTIL_OPTIONAL.equals(resolvedClass.getQualifiedName()))
		{
			return false;
		}

		final Collection<PsiType> values = resolve.getSubstitutor().getSubstitutionMap().values();
		if(values.size() == 0)
		{
			return true;
		}
		if(values.size() > 1)
		{
			return false;
		}
		final PsiType optionalTypeParameter = ContainerUtil.getFirstItem(values);
		if(optionalTypeParameter == null)
		{
			return false;
		}
		return TypeConversionUtil.isAssignable(optionalTypeParameter, exprType);
	}

	@Nonnull
	private static PsiExpression getModifiedExpression(PsiExpression expression)
	{
		final Project project = expression.getProject();
		PsiModifierListOwner toCheckNullability = null;
		if(expression instanceof PsiMethodCallExpression)
		{
			toCheckNullability = ((PsiMethodCallExpression) expression).resolveMethod();
		}
		else if(expression instanceof PsiReferenceExpression)
		{
			final PsiElement resolved = ((PsiReferenceExpression) expression).resolve();
			if(resolved instanceof PsiModifierListOwner)
			{
				toCheckNullability = (PsiModifierListOwner) resolved;
			}
		}
		final Nullness nullability = toCheckNullability == null ? Nullness.NOT_NULL : DfaPsiUtil.getElementNullability(expression.getType(), toCheckNullability);
		String methodName = nullability == Nullness.NOT_NULL ? "of" : "ofNullable";
		final String newExpressionText = JavaClassNames.JAVA_UTIL_OPTIONAL + "." + methodName + "(" + expression.getText() + ")";
		return JavaPsiFacade.getElementFactory(project).createExpressionFromText(newExpressionText, expression);
	}
}
