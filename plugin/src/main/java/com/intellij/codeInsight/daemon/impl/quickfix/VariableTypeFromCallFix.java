// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.language.psi.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import consulo.java.analysis.impl.JavaQuickFixBundle;

public class VariableTypeFromCallFix implements IntentionAction
{
	private final PsiType myExpressionType;
	private final PsiVariable myVar;

	private VariableTypeFromCallFix(@Nonnull PsiClassType type, @Nonnull PsiVariable var)
	{
		myExpressionType = type;
		myVar = var;
	}

	@Override
	@Nonnull
	public String getText()
	{
		return JavaQuickFixBundle.message("fix.variable.type.text", UsageViewUtil.getType(myVar), myVar.getName(), myExpressionType.getCanonicalText());
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("fix.variable.type.family");
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		return myExpressionType.isValid() && myVar.isValid();
	}

	@Override
	public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException
	{
		final TypeMigrationRules rules = new TypeMigrationRules(project);
		rules.setBoundScope(PsiSearchHelper.SERVICE.getInstance(project).getUseScope(myVar));

		TypeMigrationProcessor.runHighlightingTypeMigration(project, editor, rules, myVar, myExpressionType);
	}

	@Override
	public boolean startInWriteAction()
	{
		return false;
	}


	@Nonnull
	public static List<IntentionAction> getQuickFixActions(@Nonnull PsiMethodCallExpression methodCall, @Nonnull PsiExpressionList list)
	{
		final JavaResolveResult result = methodCall.getMethodExpression().advancedResolve(false);
		PsiMethod method = (PsiMethod) result.getElement();
		final PsiSubstitutor substitutor = result.getSubstitutor();
		PsiExpression[] expressions = list.getExpressions();
		if(method == null)
		{
			return Collections.emptyList();
		}
		final PsiParameter[] parameters = method.getParameterList().getParameters();
		if(parameters.length != expressions.length)
		{
			return Collections.emptyList();
		}
		List<IntentionAction> actions = new ArrayList<>();
		for(int i = 0; i < expressions.length; i++)
		{
			final PsiExpression expression = expressions[i];
			PsiType expressionType = expression.getType();
			if(expressionType instanceof PsiPrimitiveType)
			{
				expressionType = ((PsiPrimitiveType) expressionType).getBoxedType(expression);
			}
			if(expressionType == null)
			{
				continue;
			}

			final PsiParameter parameter = parameters[i];
			final PsiType formalParamType = parameter.getType();
			final PsiType parameterType = substitutor.substitute(formalParamType);
			if(parameterType.isAssignableFrom(expressionType))
			{
				continue;
			}

			final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
			if(qualifierExpression instanceof PsiReferenceExpression)
			{
				final PsiElement resolved = ((PsiReferenceExpression) qualifierExpression).resolve();
				if(resolved instanceof PsiVariable)
				{
					final PsiType varType = ((PsiVariable) resolved).getType();
					final PsiClass varClass = PsiUtil.resolveClassInType(varType);
					final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
					if(varClass != null)
					{
						final PsiSubstitutor psiSubstitutor = resolveHelper.inferTypeArguments(varClass.getTypeParameters(), parameters, expressions, PsiSubstitutor.EMPTY, resolved,
								DefaultParameterTypeInferencePolicy.INSTANCE);
						final PsiClassType appropriateVarType = JavaPsiFacade.getElementFactory(expression.getProject()).createType(varClass, psiSubstitutor);
						if(!varType.equals(appropriateVarType))
						{
							actions.add(new VariableTypeFromCallFix(appropriateVarType, (PsiVariable) resolved));
						}
						break;
					}
				}
			}
			actions.addAll(getParameterTypeChangeFixes(method, expression, parameterType));
		}
		return actions;
	}

	private static List<IntentionAction> getParameterTypeChangeFixes(@Nonnull PsiMethod method, @Nonnull PsiExpression expression, PsiType parameterType)
	{
		if(!(expression instanceof PsiReferenceExpression))
		{
			return Collections.emptyList();
		}
		List<IntentionAction> result = new ArrayList<>();
		final PsiManager manager = method.getManager();
		if(manager.isInProject(method))
		{
			final PsiMethod[] superMethods = method.findDeepestSuperMethods();
			for(PsiMethod superMethod : superMethods)
			{
				if(!manager.isInProject(superMethod))
				{
					return Collections.emptyList();
				}
			}
			final PsiElement resolve = ((PsiReferenceExpression) expression).resolve();
			if(resolve instanceof PsiVariable)
			{
				result.addAll(HighlightUtil.getChangeVariableTypeFixes((PsiVariable) resolve, parameterType));
			}
		}
		return result;
	}
}
