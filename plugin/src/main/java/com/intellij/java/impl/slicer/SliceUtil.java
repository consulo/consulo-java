/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.slicer;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaUtil;
import com.intellij.java.language.psi.*;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.language.psi.*;
import consulo.language.impl.psi.DummyHolder;
import com.intellij.java.language.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import consulo.content.scope.SearchScope;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.ast.IElementType;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.util.function.Processor;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Flow;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author cdr
 */
public class SliceUtil
{
	public static boolean processUsagesFlownDownTo(@Nonnull PsiElement expression,
			@Nonnull Processor<SliceUsage> processor,
			@Nonnull SliceUsage parent,
			@Nonnull PsiSubstitutor parentSubstitutor,
			int indexNesting,
			@Nonnull String syntheticField)
	{
		assert indexNesting >= 0 : indexNesting;
		expression = simplify(expression);
		PsiElement original = expression;
		if(expression instanceof PsiArrayAccessExpression)
		{
			// now start tracking the array instead of element
			expression = ((PsiArrayAccessExpression) expression).getArrayExpression();
			indexNesting++;
		}
		PsiElement par = expression == null ? null : expression.getParent();
		if(expression instanceof PsiExpressionList && par instanceof PsiMethodCallExpression)
		{
			// expression list ends up here if we track varargs
			PsiMethod method = ((PsiMethodCallExpression) par).resolveMethod();
			if(method != null)
			{
				int parametersCount = method.getParameterList().getParametersCount();
				if(parametersCount != 0)
				{
					// unfold varargs list into individual expressions
					PsiExpression[] expressions = ((PsiExpressionList) expression).getExpressions();
					if(indexNesting != 0)
					{
						// should skip not-vararg arguments
						for(int i = parametersCount - 1; i < expressions.length; i++)
						{
							PsiExpression arg = expressions[i];
							if(!handToProcessor(arg, processor, parent, parentSubstitutor, indexNesting - 1, syntheticField))
							{
								return false;
							}
						}
					}
					return true;
				}
			}
		}

		boolean needToReportDeclaration = false;
		if(expression instanceof PsiReferenceExpression)
		{
			PsiElement element = SliceForwardUtil.complexify(expression);
			if(element instanceof PsiExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression) element))
			{
				PsiExpression rightSide = ((PsiAssignmentExpression) element.getParent()).getRExpression();
				return rightSide == null || handToProcessor(rightSide, processor, parent, parentSubstitutor, indexNesting, syntheticField);
			}
			PsiReferenceExpression ref = (PsiReferenceExpression) expression;
			JavaResolveResult result = ref.advancedResolve(false);
			parentSubstitutor = result.getSubstitutor().putAll(parentSubstitutor);
			PsiElement resolved = result.getElement();
			if(resolved instanceof PsiCompiledElement)
			{
				resolved = resolved.getNavigationElement();
			}
			if(resolved instanceof PsiMethod && expression.getParent() instanceof PsiMethodCallExpression)
			{
				return processUsagesFlownDownTo(expression.getParent(), processor, parent, parentSubstitutor, indexNesting, syntheticField);
			}
			if(!(resolved instanceof PsiVariable))
			{
				return true;
			}
			// check for container item modifications, like "array[i] = expression;"
			addContainerReferences((PsiVariable) resolved, processor, parent, parentSubstitutor, indexNesting, syntheticField);

			needToReportDeclaration = true;
			expression = resolved;
		}
		if(expression instanceof PsiVariable)
		{
			PsiVariable variable = (PsiVariable) expression;
			Collection<PsiExpression> values = DfaUtil.getVariableValues(variable, original);
			PsiExpression initializer = variable.getInitializer();
			if(values.isEmpty() && initializer != null)
			{
				values = Collections.singletonList(initializer);
			}
			final Set<PsiExpression> expressions = new HashSet<PsiExpression>(values);
			if(initializer != null && expressions.isEmpty())
			{
				expressions.add(initializer);
			}
			boolean initializerReported = false;
			for(PsiExpression exp : expressions)
			{
				if(!handToProcessor(exp, processor, parent, parentSubstitutor, indexNesting, syntheticField))
				{
					return false;
				}
				if(exp == initializer)
				{
					initializerReported = true;
				}
			}

			if(!initializerReported && needToReportDeclaration)
			{ // otherwise we'll reach var declaration anyway because it is the initializer' parent
				// parameter or variable declaration can be far away from its usage (except for variable initializer) so report it separately
				return handToProcessor(variable, processor, parent, parentSubstitutor, indexNesting, syntheticField);
			}

			if(variable instanceof PsiField)
			{
				return processFieldUsages((PsiField) variable, parent, parentSubstitutor, processor);
			}
			else if(variable instanceof PsiParameter)
			{
				return processParameterUsages((PsiParameter) variable, parent, parentSubstitutor, indexNesting, syntheticField, processor);
			}
		}
		if(expression instanceof PsiMethodCallExpression)
		{ // ctr call can't return value or be container get, so don't use PsiCall here
			PsiMethod method = ((PsiMethodCallExpression) expression).resolveMethod();
			Flow anno = isMethodFlowAnnotated(method);
			if(anno != null)
			{
				String target = anno.target();
				if(target.equals(Flow.DEFAULT_TARGET))
				{
					target = Flow.RETURN_METHOD_TARGET;
				}
				if(target.equals(Flow.RETURN_METHOD_TARGET))
				{
					PsiExpression qualifier = ((PsiMethodCallExpression) expression).getMethodExpression().getQualifierExpression();
					if(qualifier != null)
					{
						int nesting = calcNewIndexNesting(indexNesting, anno);
						String source = anno.source();
						if(source.equals(Flow.DEFAULT_SOURCE))
						{
							source = Flow.THIS_SOURCE;
						}
						String synthetic = StringUtil.trimStart(StringUtil.trimStart(source, Flow.THIS_SOURCE), ".");
						return processUsagesFlownDownTo(qualifier, processor, parent, parentSubstitutor, nesting, synthetic);
					}
				}
			}
			return processMethodReturnValue((PsiMethodCallExpression) expression, processor, parent, parentSubstitutor);
		}
		if(expression instanceof PsiConditionalExpression)
		{
			PsiConditionalExpression conditional = (PsiConditionalExpression) expression;
			PsiExpression thenE = conditional.getThenExpression();
			PsiExpression elseE = conditional.getElseExpression();
			if(thenE != null && !handToProcessor(thenE, processor, parent, parentSubstitutor, indexNesting, syntheticField))
			{
				return false;
			}
			if(elseE != null && !handToProcessor(elseE, processor, parent, parentSubstitutor, indexNesting, syntheticField))
			{
				return false;
			}
		}
		if(expression instanceof PsiAssignmentExpression)
		{
			PsiAssignmentExpression assignment = (PsiAssignmentExpression) expression;
			IElementType tokenType = assignment.getOperationTokenType();
			PsiExpression rExpression = assignment.getRExpression();

			if(tokenType == JavaTokenType.EQ && rExpression != null)
			{
				return processUsagesFlownDownTo(rExpression, processor, parent, parentSubstitutor, indexNesting, syntheticField);
			}
		}
		if(indexNesting != 0)
		{
			// consider container creation
			PsiElement initializer = expression instanceof PsiNewExpression ? ((PsiNewExpression) expression).getArrayInitializer() : expression;
			if(initializer instanceof PsiArrayInitializerExpression)
			{
				for(PsiExpression init : ((PsiArrayInitializerExpression) initializer).getInitializers())
				{
					if(!handToProcessor(init, processor, parent, parentSubstitutor, indexNesting - 1, syntheticField))
					{
						return false;
					}
				}
			}

			// check for constructor put arguments
			if(expression instanceof PsiNewExpression && !processContainerPutArguments((PsiNewExpression) expression, processor, parent,
					parentSubstitutor, indexNesting, syntheticField))
			{
				return false;
			}
		}
		return true;
	}

	private static Flow isMethodFlowAnnotated(PsiMethod method)
	{
		if(method == null)
		{
			return null;
		}
		return AnnotationUtil.findAnnotationInHierarchy(method, Flow.class);
	}

	private static Flow isParamFlowAnnotated(PsiMethod method, int paramIndex)
	{
		PsiParameter[] parameters = method.getParameterList().getParameters();
		if(parameters.length <= paramIndex)
		{
			if(parameters.length != 0 && parameters[parameters.length - 1].isVarArgs())
			{
				paramIndex = parameters.length - 1;
			}
			else
			{
				return null;
			}
		}
		return AnnotationUtil.findAnnotationInHierarchy(parameters[paramIndex], Flow.class);
	}

	private static PsiElement simplify(@Nonnull PsiElement expression)
	{
		if(expression instanceof PsiParenthesizedExpression)
		{
			return simplify(((PsiParenthesizedExpression) expression).getExpression());
		}
		if(expression instanceof PsiTypeCastExpression)
		{
			return simplify(((PsiTypeCastExpression) expression).getOperand());
		}
		return expression;
	}

	private static boolean handToProcessor(@Nonnull PsiElement expression,
			@Nonnull Processor<SliceUsage> processor,
			@Nonnull SliceUsage parent,
			@Nonnull PsiSubstitutor substitutor,
			int indexNesting,
			@Nonnull String syntheticField)
	{
		final PsiElement realExpression = expression.getParent() instanceof DummyHolder ? expression.getParent().getContext() : expression;
		assert realExpression != null;
		if(!(realExpression instanceof PsiCompiledElement))
		{
			SliceUsage usage = createSliceUsage(realExpression, parent, substitutor, indexNesting, syntheticField);
			if(!processor.process(usage))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean processMethodReturnValue(@Nonnull final PsiMethodCallExpression methodCallExpr,
			@Nonnull final Processor<SliceUsage> processor,
			@Nonnull final SliceUsage parent,
			@Nonnull final PsiSubstitutor parentSubstitutor)
	{
		final JavaResolveResult resolved = methodCallExpr.resolveMethodGenerics();
		PsiElement r = resolved.getElement();
		if(r instanceof PsiCompiledElement)
		{
			r = r.getNavigationElement();
		}
		if(!(r instanceof PsiMethod))
		{
			return true;
		}
		PsiMethod methodCalled = (PsiMethod) r;

		PsiType returnType = methodCalled.getReturnType();
		if(returnType == null)
		{
			return true;
		}

		final PsiType parentType = parentSubstitutor.substitute(methodCallExpr.getType());
		final PsiSubstitutor substitutor = resolved.getSubstitutor().putAll(parentSubstitutor);
		Collection<PsiMethod> overrides = new HashSet<PsiMethod>(OverridingMethodsSearch.search(methodCalled, parent.getScope().toSearchScope(),
				true).findAll());
		overrides.add(methodCalled);

		final boolean[] result = {true};
		for(PsiMethod override : overrides)
		{
			if(!result[0])
			{
				break;
			}
			if(override instanceof PsiCompiledElement)
			{
				override = (PsiMethod) override.getNavigationElement();
			}
			if(!parent.getScope().contains(override))
			{
				continue;
			}

			final PsiCodeBlock body = override.getBody();
			if(body == null)
			{
				continue;
			}

			final PsiSubstitutor s = methodCalled == override ? substitutor : MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodCalled
					.getSignature(substitutor), override.getSignature(substitutor));
			final PsiSubstitutor superSubstitutor = s == null ? parentSubstitutor : s;

			body.accept(new JavaRecursiveElementWalkingVisitor()
			{
				@Override
				public void visitClass(PsiClass aClass)
				{
				}

				@Override
				public void visitLambdaExpression(PsiLambdaExpression expression)
				{
				}

				@Override
				public void visitReturnStatement(final PsiReturnStatement statement)
				{
					PsiExpression returnValue = statement.getReturnValue();
					if(returnValue == null)
					{
						return;
					}
					PsiType right = superSubstitutor.substitute(superSubstitutor.substitute(returnValue.getType()));
					if(right == null || !TypeConversionUtil.isAssignable(parentType, right))
					{
						return;
					}
					if(!handToProcessor(returnValue, processor, parent, substitutor, parent.indexNesting, ""))
					{
						stopWalking();
						result[0] = false;
					}
				}
			});
		}

		return result[0];
	}

	private static boolean processFieldUsages(@Nonnull final PsiField field,
			@Nonnull final SliceUsage parent,
			@Nonnull final PsiSubstitutor parentSubstitutor,
			@Nonnull final Processor<SliceUsage> processor)
	{
		if(field.hasInitializer())
		{
			PsiExpression initializer = field.getInitializer();
			if(initializer != null && !(field instanceof PsiCompiledElement))
			{
				if(!handToProcessor(initializer, processor, parent, parentSubstitutor, parent.indexNesting, ""))
				{
					return false;
				}
			}
		}
		SearchScope searchScope = parent.getScope().toSearchScope();
		return ReferencesSearch.search(field, searchScope).forEach(new Processor<PsiReference>()
		{
			@Override
			public boolean process(final PsiReference reference)
			{
				ProgressManager.checkCanceled();
				PsiElement element = reference.getElement();
				if(!(element instanceof PsiReferenceExpression))
				{
					return true;
				}
				if(element instanceof PsiCompiledElement)
				{
					element = element.getNavigationElement();
					if(!parent.getScope().contains(element))
					{
						return true;
					}
				}
				final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) element;
				PsiElement parentExpr = referenceExpression.getParent();
				if(PsiUtil.isOnAssignmentLeftHand(referenceExpression))
				{
					PsiExpression rExpression = ((PsiAssignmentExpression) parentExpr).getRExpression();
					PsiType rtype = rExpression.getType();
					PsiType ftype = field.getType();
					if(TypeConversionUtil.isAssignable(parentSubstitutor.substitute(ftype), parentSubstitutor.substitute(rtype)))
					{
						return handToProcessor(rExpression, processor, parent, parentSubstitutor, parent.indexNesting, "");
					}
				}
				if(parentExpr instanceof PsiPrefixExpression && ((PsiPrefixExpression) parentExpr).getOperand() == referenceExpression && ((
						(PsiPrefixExpression) parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiPrefixExpression) parentExpr)
						.getOperationTokenType() == JavaTokenType.MINUSMINUS))
				{
					PsiPrefixExpression prefixExpression = (PsiPrefixExpression) parentExpr;
					return handToProcessor(prefixExpression, processor, parent, parentSubstitutor, parent.indexNesting, "");
				}
				if(parentExpr instanceof PsiPostfixExpression && ((PsiPostfixExpression) parentExpr).getOperand() == referenceExpression && ((
						(PsiPostfixExpression) parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiPostfixExpression) parentExpr)
						.getOperationTokenType() == JavaTokenType.MINUSMINUS))
				{
					PsiPostfixExpression postfixExpression = (PsiPostfixExpression) parentExpr;
					return handToProcessor(postfixExpression, processor, parent, parentSubstitutor, parent.indexNesting, "");
				}
				return true;
			}
		});
	}

	@Nonnull
	public static SliceUsage createSliceUsage(@Nonnull PsiElement element,
			@Nonnull SliceUsage parent,
			@Nonnull PsiSubstitutor substitutor,
			int indexNesting,
			@Nonnull String syntheticField)
	{
		return new SliceUsage(simplify(element), parent, substitutor, indexNesting, syntheticField);
	}

	@Nonnull
	public static SliceUsage createTooComplexDFAUsage(@Nonnull PsiElement element, @Nonnull SliceUsage parent, @Nonnull PsiSubstitutor substitutor)
	{
		return new SliceTooComplexDFAUsage(simplify(element), parent, substitutor);
	}

	private static boolean processParameterUsages(@Nonnull final PsiParameter parameter,
			@Nonnull final SliceUsage parent,
			@Nonnull final PsiSubstitutor parentSubstitutor,
			final int indexNesting,
			@Nonnull final String syntheticField,
			@Nonnull final Processor<SliceUsage> processor)
	{
		PsiElement declarationScope = parameter.getDeclarationScope();
		if(declarationScope instanceof PsiForeachStatement)
		{
			PsiForeachStatement statement = (PsiForeachStatement) declarationScope;
			PsiExpression iterated = statement.getIteratedValue();
			if(statement.getIterationParameter() == parameter && iterated != null)
			{
				if(!handToProcessor(iterated, processor, parent, parentSubstitutor, indexNesting + 1, syntheticField))
				{
					return false;
				}
			}
			return true;
		}
		if(!(declarationScope instanceof PsiMethod))
		{
			return true;
		}

		final PsiMethod method = (PsiMethod) declarationScope;
		final PsiType actualParameterType = parameter.getType();

		final PsiParameter[] actualParameters = method.getParameterList().getParameters();
		final int paramSeqNo = ArrayUtil.find(actualParameters, parameter);
		assert paramSeqNo != -1;

		Collection<PsiMethod> superMethods = new HashSet<PsiMethod>(Arrays.asList(method.findDeepestSuperMethods()));
		superMethods.add(method);

		final Set<PsiReference> processed = new HashSet<PsiReference>(); //usages of super method and overridden method can overlap
		for(final PsiMethod superMethod : superMethods)
		{
			if(!MethodReferencesSearch.search(superMethod, parent.getScope().toSearchScope(), true).forEach(new Processor<PsiReference>()
			{
				@Override
				public boolean process(final PsiReference reference)
				{
					ProgressManager.checkCanceled();
					synchronized(processed)
					{
						if(!processed.add(reference))
						{
							return true;
						}
					}
					PsiElement refElement = reference.getElement();
					PsiExpressionList argumentList;
					JavaResolveResult result;
					if(refElement instanceof PsiCall)
					{
						// the case of enum constant decl
						PsiCall call = (PsiCall) refElement;
						argumentList = call.getArgumentList();
						result = call.resolveMethodGenerics();
					}
					else
					{
						PsiElement element = refElement.getParent();
						if(element instanceof PsiCompiledElement)
						{
							return true;
						}
						if(element instanceof PsiAnonymousClass)
						{
							PsiAnonymousClass anon = (PsiAnonymousClass) element;
							argumentList = anon.getArgumentList();
							PsiElement callExp = element.getParent();
							if(!(callExp instanceof PsiCallExpression))
							{
								return true;
							}
							result = ((PsiCall) callExp).resolveMethodGenerics();
						}
						else
						{
							if(!(element instanceof PsiCall))
							{
								return true;
							}
							PsiCall call = (PsiCall) element;
							argumentList = call.getArgumentList();
							result = call.resolveMethodGenerics();
						}
					}
					PsiSubstitutor substitutor = result.getSubstitutor();

					PsiExpression[] expressions = argumentList.getExpressions();
					if(paramSeqNo >= expressions.length)
					{
						return true;
					}
					PsiElement passExpression;
					PsiType actualExpressionType;
					if(actualParameterType instanceof PsiEllipsisType)
					{
						passExpression = argumentList;
						actualExpressionType = expressions[paramSeqNo].getType();
					}
					else
					{
						passExpression = expressions[paramSeqNo];
						actualExpressionType = ((PsiExpression) passExpression).getType();
					}

					Project project = argumentList.getProject();
					PsiElement element = result.getElement();
					if(element instanceof PsiCompiledElement)
					{
						element = element.getNavigationElement();
					}

					// for erased method calls for which we cannot determine target substitutor,
					// rely on call argument types. I.e. new Pair(1,2) -> Pair<Integer, Integer>
					if(element instanceof PsiTypeParameterListOwner && PsiUtil.isRawSubstitutor((PsiTypeParameterListOwner) element, substitutor))
					{
						PsiTypeParameter[] typeParameters = substitutor.getSubstitutionMap().keySet().toArray(new PsiTypeParameter[0]);

						PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
						substitutor = resolveHelper.inferTypeArguments(typeParameters, actualParameters, expressions, parentSubstitutor,
								argumentList, DefaultParameterTypeInferencePolicy.INSTANCE);
					}

					substitutor = removeRawMappingsLeftFromResolve(substitutor);

					PsiSubstitutor combined = unify(substitutor, parentSubstitutor, project);
					if(combined == null)
					{
						return true;
					}
					//PsiType substituted = combined.substitute(passExpression.getType());
					PsiType substituted = combined.substitute(actualExpressionType);
					if(substituted instanceof PsiPrimitiveType)
					{
						final PsiClassType boxedType = ((PsiPrimitiveType) substituted).getBoxedType(argumentList);
						substituted = boxedType != null ? boxedType : substituted;
					}
					if(substituted == null)
					{
						return true;
					}
					PsiType typeToCheck;
					if(actualParameterType instanceof PsiEllipsisType)
					{
						// there may be the case of passing the vararg argument to the other vararg method: foo(int... ints) { bar(ints); } bar(int.
						// .. ints) {}
						if(TypeConversionUtil.areTypesConvertible(substituted, actualParameterType))
						{
							return handToProcessor(expressions[paramSeqNo], processor, parent, combined, indexNesting, syntheticField);
						}
						typeToCheck = ((PsiEllipsisType) actualParameterType).getComponentType();
					}
					else
					{
						typeToCheck = actualParameterType;
					}
					if(!TypeConversionUtil.areTypesConvertible(substituted, typeToCheck))
					{
						return true;
					}

					return handToProcessor(passExpression, processor, parent, combined, indexNesting, syntheticField);
				}
			}))
			{
				return false;
			}
		}

		return true;
	}

	private static void addContainerReferences(@Nonnull PsiVariable variable,
			@Nonnull final Processor<SliceUsage> processor,
			@Nonnull final SliceUsage parent,
			@Nonnull final PsiSubstitutor parentSubstitutor,
			final int indexNesting,
			@Nonnull final String syntheticField)
	{
		if(indexNesting != 0)
		{
			ReferencesSearch.search(variable).forEach(new Processor<PsiReference>()
			{
				@Override
				public boolean process(PsiReference reference)
				{
					PsiElement element = reference.getElement();
					if(element instanceof PsiExpression && !element.getManager().areElementsEquivalent(element, parent.getElement()))
					{
						PsiExpression expression = (PsiExpression) element;
						if(!addContainerItemModification(expression, processor, parent, parentSubstitutor, indexNesting, syntheticField))
						{
							return false;
						}
					}
					return true;
				}
			});
		}
	}

	private static boolean addContainerItemModification(@Nonnull PsiExpression expression,
			@Nonnull Processor<SliceUsage> processor,
			@Nonnull SliceUsage parent,
			@Nonnull PsiSubstitutor parentSubstitutor,
			int indexNesting,
			@Nonnull String syntheticField)
	{
		PsiElement parentElement = expression.getParent();
		if(parentElement instanceof PsiArrayAccessExpression &&
				((PsiArrayAccessExpression) parentElement).getArrayExpression() == expression &&
				PsiUtil.isAccessedForWriting((PsiExpression) parentElement))
		{

			if(PsiUtil.isOnAssignmentLeftHand((PsiExpression) parentElement))
			{
				PsiExpression rightSide = ((PsiAssignmentExpression) parentElement.getParent()).getRExpression();
				return rightSide == null || handToProcessor(rightSide, processor, parent, parentSubstitutor, indexNesting - 1, syntheticField);
			}
		}
		PsiElement grand = parentElement == null ? null : parentElement.getParent();
		if(grand instanceof PsiCallExpression)
		{
			if(!processContainerPutArguments((PsiCallExpression) grand, processor, parent, parentSubstitutor, indexNesting, syntheticField))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean processContainerPutArguments(PsiCallExpression call,
			Processor<SliceUsage> processor,
			SliceUsage parent,
			PsiSubstitutor parentSubstitutor,
			int indexNesting,
			@Nonnull String syntheticField)
	{
		assert indexNesting != 0;
		JavaResolveResult result = call.resolveMethodGenerics();
		PsiMethod method = (PsiMethod) result.getElement();
		if(method != null)
		{
			int parametersCount = method.getParameterList().getParametersCount();
			Flow[] annotations = new Flow[parametersCount];
			for(int i = 0; i < parametersCount; i++)
			{
				annotations[i] = isParamFlowAnnotated(method, i);
			}

			PsiExpression[] expressions = call.getArgumentList().getExpressions();
			PsiParameter[] parameters = method.getParameterList().getParameters();
			for(int i = 0; i < expressions.length; i++)
			{
				PsiExpression argument = expressions[i];
				Flow anno;
				PsiParameter parameter;
				if(i >= parameters.length)
				{
					if(parameters.length != 0 && parameters[parameters.length - 1].isVarArgs())
					{
						anno = annotations[parameters.length - 1];
						parameter = parameters[parameters.length - 1];
					}
					else
					{
						break;
					}
				}
				else
				{
					anno = annotations[i];
					parameter = parameters[i];
				}
				if(anno != null)
				{
					String target = anno.target();
					if(target.equals(Flow.DEFAULT_TARGET))
					{
						target = Flow.THIS_TARGET;
					}
					if(target.startsWith(Flow.THIS_TARGET))
					{
						String paramSynthetic = StringUtil.trimStart(StringUtil.trimStart(target, Flow.THIS_TARGET), ".");
						if(paramSynthetic.equals(syntheticField))
						{
							PsiSubstitutor substitutor = unify(result.getSubstitutor(), parentSubstitutor, argument.getProject());
							int nesting = calcNewIndexNesting(indexNesting, anno);
							if(!handToProcessor(argument, processor, parent, substitutor, nesting, paramSynthetic))
							{
								return false;
							}
						}
					}
				}
				// check flow parameter to another param
				for(int si = 0; si < annotations.length; si++)
				{
					if(si == i)
					{
						continue;
					}
					Flow sourceAnno = annotations[si];
					if(sourceAnno == null)
					{
						continue;
					}
					if(sourceAnno.target().equals(parameter.getName()))
					{
						int newNesting = calcNewIndexNesting(indexNesting, sourceAnno);
						PsiExpression sourceArgument = expressions[si];
						PsiSubstitutor substitutor = unify(result.getSubstitutor(), parentSubstitutor, argument.getProject());
						if(!handToProcessor(sourceArgument, processor, parent, substitutor, newNesting, syntheticField))
						{
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	private static int calcNewIndexNesting(int indexNesting, Flow anno)
	{
		int nestingDelta = (anno.sourceIsContainer() ? 1 : 0) - (anno.targetIsContainer() ? 1 : 0);
		return indexNesting + nestingDelta;
	}

	@Nonnull
	private static PsiSubstitutor removeRawMappingsLeftFromResolve(@Nonnull PsiSubstitutor substitutor)
	{
		Map<PsiTypeParameter, PsiType> map = null;
		for(Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet())
		{
			if(entry.getValue() == null)
			{
				if(map == null)
				{
					map = new HashMap<PsiTypeParameter, PsiType>();
				}
				map.put(entry.getKey(), entry.getValue());
			}
		}
		if(map == null)
		{
			return substitutor;
		}
		Map<PsiTypeParameter, PsiType> newMap = new HashMap<PsiTypeParameter, PsiType>(substitutor.getSubstitutionMap());
		newMap.keySet().removeAll(map.keySet());
		return PsiSubstitutor.createSubstitutor(newMap);
	}

	@Nullable
	private static PsiSubstitutor unify(@Nonnull PsiSubstitutor substitutor, @Nonnull PsiSubstitutor parentSubstitutor, @Nonnull Project project)
	{
		Map<PsiTypeParameter, PsiType> newMap = new HashMap<PsiTypeParameter, PsiType>(substitutor.getSubstitutionMap());

		for(Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet())
		{
			PsiTypeParameter typeParameter = entry.getKey();
			PsiType type = entry.getValue();
			PsiClass resolved = PsiUtil.resolveClassInType(type);
			if(!parentSubstitutor.getSubstitutionMap().containsKey(typeParameter))
			{
				continue;
			}
			PsiType parentType = parentSubstitutor.substitute(parentSubstitutor.substitute(typeParameter));

			if(resolved instanceof PsiTypeParameter)
			{
				PsiTypeParameter res = (PsiTypeParameter) resolved;
				newMap.put(res, parentType);
			}
			else if(!Comparing.equal(type, parentType))
			{
				return null; // cannot unify
			}
		}
		return JavaPsiFacade.getElementFactory(project).createSubstitutor(newMap);
	}
}

