/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.ArgumentFixerActionFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.MethodArgumentFix;
import org.jetbrains.annotations.Nls;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.java.analysis.impl.JavaQuickFixBundle;

/**
 * @author Dmitry Batkovich
 */
public class WrapLongWithMathToIntExactFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction
{
	public final static MyMethodArgumentFixerFactory REGISTAR = new MyMethodArgumentFixerFactory();

	private final PsiType myType;

	public WrapLongWithMathToIntExactFix(final PsiType type, final @Nonnull PsiExpression expression)
	{
		super(expression);
		myType = type;
	}

	@Nonnull
	@Override
	public String getText()
	{
		return getFamilyName();
	}

	@Override
	public void invoke(@Nonnull Project project,
			@Nonnull PsiFile file,
			@Nullable Editor editor,
			@Nonnull PsiElement startElement,
			@Nonnull PsiElement endElement)
	{
		startElement.replace(getModifiedExpression(startElement));
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
	{
		return startElement.isValid() &&
				startElement.getManager().isInProject(startElement) &&
				PsiUtil.isLanguageLevel8OrHigher(startElement) &&
				areSameTypes(myType, PsiType.INT) &&
				areSameTypes(((PsiExpression) startElement).getType(), PsiType.LONG);
	}

	private static boolean areSameTypes(@jakarta.annotation.Nullable PsiType type, @Nonnull PsiPrimitiveType expected)
	{
		return !(type == null ||
				!type.isValid() ||
				(!type.equals(expected) && !expected.getBoxedTypeName().equals(type.getCanonicalText(false))));
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("wrap.long.with.math.to.int.text");
	}

	private static PsiElement getModifiedExpression(PsiElement expression)
	{
		return JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText("java.lang.Math.toIntExact(" + expression.getText() + ")", expression);
	}

	private static class MyMethodArgumentFix extends MethodArgumentFix implements HighPriorityAction
	{

		protected MyMethodArgumentFix(@Nonnull PsiExpressionList list, int i, @jakarta.annotation.Nonnull PsiType toType, @Nonnull ArgumentFixerActionFactory fixerActionFactory)
		{
			super(list, i, toType, fixerActionFactory);
		}

		@Nls
		@Nonnull
		@Override
		public String getText()
		{
			return myArgList.getExpressions().length == 1 ? JavaQuickFixBundle.message("wrap.long.with.math.to.int.parameter.single.text") : JavaQuickFixBundle.message("wrap.long.with.math.to.int.parameter" +
					".multiple.text", myIndex + 1);
		}

		@Override
		public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
		{
			return PsiUtil.isLanguageLevel8OrHigher(file) && super.isAvailable(project, editor, file);
		}
	}

	public static class MyMethodArgumentFixerFactory extends ArgumentFixerActionFactory
	{
		@jakarta.annotation.Nullable
		@Override
		protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException
		{
			return areSameTypes(toType, PsiType.INT) ? (PsiExpression) getModifiedExpression(expression) : null;
		}

		@Override
		public boolean areTypesConvertible(final PsiType exprType, final PsiType parameterType, @jakarta.annotation.Nonnull final PsiElement context)
		{
			return parameterType.isConvertibleFrom(exprType) || (areSameTypes(parameterType, PsiType.INT) && areSameTypes(exprType, PsiType.LONG));
		}

		@Override
		public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType)
		{
			return new MyMethodArgumentFix(list, i, toType, this);
		}
	}
}
