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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ToArrayCallWithZeroLengthArrayArgumentInspectionBase extends BaseInspection
{
	@Override
	@Nls
	@Nonnull
	public String getDisplayName()
	{
		return InspectionGadgetsLocalize.toArrayCallWithZeroLengthArrayArgumentDisplayName().get();
	}

	@Override
	@Nonnull
	protected String buildErrorString(Object... infos)
	{
		final PsiExpression argument = (PsiExpression) infos[1];
		return InspectionGadgetsLocalize.toArrayCallWithZeroLengthArrayArgumentProblemDescriptor(argument.getText()).get();
	}

	@Override
	public BaseInspectionVisitor buildVisitor()
	{
		return new ToArrayCallWithZeroLengthArrayArgument();
	}

	private static class ToArrayCallWithZeroLengthArrayArgument extends BaseInspectionVisitor
	{

		@Override
		public void visitMethodCallExpression(PsiMethodCallExpression expression)
		{
			super.visitMethodCallExpression(expression);
			final PsiReferenceExpression methodExpression = expression.getMethodExpression();
			@NonNls final String methodName = methodExpression.getReferenceName();
			if(!"toArray".equals(methodName))
			{
				return;
			}
			final PsiExpressionList argumentList = expression.getArgumentList();
			final PsiExpression[] arguments = argumentList.getExpressions();
			if(arguments.length != 1)
			{
				return;
			}
			final PsiExpression argument = arguments[0];
			final PsiType type = argument.getType();
			if(!(type instanceof PsiArrayType))
			{
				return;
			}
			if(type.getArrayDimensions() != 1)
			{
				return;
			}
			if(argument instanceof PsiReferenceExpression)
			{
				final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) argument;
				final PsiElement element = referenceExpression.resolve();
				if(!(element instanceof PsiField))
				{
					return;
				}
				final PsiField field = (PsiField) element;
				if(!CollectionUtils.isConstantEmptyArray(field))
				{
					return;
				}
			}
			else if(!ConstructionUtils.isEmptyArrayInitializer(argument))
			{
				return;
			}
			final PsiMethod method = expression.resolveMethod();
			if(method == null)
			{
				return;
			}
			final PsiClass containingClass = method.getContainingClass();
			if(!InheritanceUtil.isInheritor(containingClass, JavaClassNames.JAVA_UTIL_COLLECTION))
			{
				return;
			}
			registerMethodCallError(expression, expression, argument);
		}
	}
}
