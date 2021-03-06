/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.codeInsight.completion.CompletionProvider;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class SameSignatureCallParametersProvider implements CompletionProvider
{
	static final PsiElementPattern.Capture<PsiElement> IN_CALL_ARGUMENT = psiElement().beforeLeaf(psiElement(JavaTokenType.RPARENTH)).afterLeaf("(").withParent(psiElement(PsiReferenceExpression
			.class).withParent(psiElement(PsiExpressionList.class).withParent(PsiCall.class)));

	@Override
	public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result)
	{
		addSignatureItems(parameters, result);
	}

	void addSignatureItems(@Nonnull CompletionParameters parameters, @Nonnull Consumer<LookupElement> result)
	{
		final PsiCall methodCall = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiCall.class);
		assert methodCall != null;
		Set<Pair<PsiMethod, PsiSubstitutor>> candidates = getCallCandidates(methodCall);

		PsiMethod container = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
		while(container != null)
		{
			for(final Pair<PsiMethod, PsiSubstitutor> candidate : candidates)
			{
				if(container.getParameterList().getParametersCount() > 1 && candidate.first.getParameterList().getParametersCount() > 1)
				{
					PsiMethod from = getMethodToTakeParametersFrom(container, candidate.first, candidate.second);
					if(from != null)
					{
						result.consume(createParametersLookupElement(from, methodCall, candidate.first));
					}
				}
			}

			container = PsiTreeUtil.getParentOfType(container, PsiMethod.class);

		}
	}

	private static LookupElement createParametersLookupElement(final PsiMethod takeParametersFrom, PsiElement call, PsiMethod invoked)
	{
		final PsiParameter[] parameters = takeParametersFrom.getParameterList().getParameters();
		final String lookupString = StringUtil.join(parameters, PsiNamedElement::getName, ", ");

		// TODO [VISTALL] new icon
		Image ppIcon = ImageEffects.transparent(AllIcons.Nodes.Parameter);
		LookupElementBuilder element = LookupElementBuilder.create(lookupString).withIcon(ppIcon);
		if(PsiTreeUtil.isAncestor(takeParametersFrom, call, true))
		{
			element = element.withInsertHandler(new InsertHandler<LookupElement>()
			{
				@Override
				public void handleInsert(InsertionContext context, LookupElement item)
				{
					context.commitDocument();
					for(PsiParameter parameter : CompletionUtil.getOriginalOrSelf(takeParametersFrom).getParameterList().getParameters())
					{
						VariableLookupItem.makeFinalIfNeeded(context, parameter);
					}
				}
			});
		}
		element.putUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS, Boolean.TRUE);

		return TailTypeDecorator.withTail(element, ExpectedTypesProvider.getFinalCallParameterTailType(call, invoked.getReturnType(), invoked));
	}

	private static Set<Pair<PsiMethod, PsiSubstitutor>> getCallCandidates(PsiCall expression)
	{
		Set<Pair<PsiMethod, PsiSubstitutor>> candidates = ContainerUtil.newLinkedHashSet();
		JavaResolveResult[] results;
		if(expression instanceof PsiMethodCallExpression)
		{
			results = ((PsiMethodCallExpression) expression).getMethodExpression().multiResolve(false);
		}
		else
		{
			results = new JavaResolveResult[]{expression.resolveMethodGenerics()};
		}

		PsiMethod toExclude = ExpressionUtils.isConstructorInvocation(expression) ? PsiTreeUtil.getParentOfType(expression, PsiMethod.class) : null;

		for(final JavaResolveResult candidate : results)
		{
			final PsiElement element = candidate.getElement();
			if(element instanceof PsiMethod)
			{
				final PsiClass psiClass = ((PsiMethod) element).getContainingClass();
				if(psiClass != null)
				{
					for(Pair<PsiMethod, PsiSubstitutor> overload : psiClass.findMethodsAndTheirSubstitutorsByName(((PsiMethod) element).getName(), true))
					{
						if(overload.first != toExclude)
						{
							candidates.add(Pair.create(overload.first, candidate.getSubstitutor().putAll(overload.second)));
						}
					}
					break;
				}
			}
		}
		return candidates;
	}


	@Nullable
	private static PsiMethod getMethodToTakeParametersFrom(PsiMethod place, PsiMethod invoked, PsiSubstitutor substitutor)
	{
		if(PsiSuperMethodUtil.isSuperMethod(place, invoked))
		{
			return place;
		}

		Map<String, PsiType> requiredNames = ContainerUtil.newHashMap();
		final PsiParameter[] parameters = place.getParameterList().getParameters();
		final PsiParameter[] callParams = invoked.getParameterList().getParameters();
		if(callParams.length > parameters.length)
		{
			return null;
		}

		final boolean checkNames = invoked.isConstructor();
		boolean sameTypes = true;
		for(int i = 0; i < callParams.length; i++)
		{
			PsiParameter callParam = callParams[i];
			PsiParameter parameter = parameters[i];
			requiredNames.put(callParam.getName(), substitutor.substitute(callParam.getType()));
			if(checkNames && !Comparing.equal(parameter.getName(), callParam.getName()) || !Comparing.equal(parameter.getType(), substitutor.substitute(callParam.getType())))
			{
				sameTypes = false;
			}
		}

		if(sameTypes && callParams.length == parameters.length)
		{
			return place;
		}

		for(PsiParameter parameter : parameters)
		{
			PsiType type = requiredNames.remove(parameter.getName());
			if(type != null && !parameter.getType().equals(type))
			{
				return null;
			}
		}

		return requiredNames.isEmpty() ? invoked : null;
	}
}
