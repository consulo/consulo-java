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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.icons.AllIcons;
import com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.java.language.psi.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.java.language.patterns.PsiJavaElementPattern;
import com.intellij.java.language.patterns.PsiMethodPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.codeInsight.completion.JavaCompletionContributor.isInJavaContext;
import static com.intellij.java.language.patterns.PsiJavaPatterns.*;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaMethodHandleCompletionContributor extends CompletionContributor
{

	// MethodHandle for constructors and methods
	private static final Set<String> METHOD_HANDLE_FACTORY_NAMES = ContainerUtil.immutableSet(FIND_CONSTRUCTOR, FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL);

	private static final PsiJavaElementPattern.Capture<PsiElement> METHOD_TYPE_ARGUMENT_PATTERN = psiJavaElement().afterLeaf(",").withParent(or(psiExpression().methodCallParameter(1, methodPattern
			(FIND_CONSTRUCTOR)), psiExpression().methodCallParameter(2, methodPattern(FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL))));


	// VarHandle for fields and synthetic MethodHandle for field getters/setters
	private static final Set<String> FIELD_HANDLE_FACTORY_NAMES = ContainerUtil.immutableSet(FIND_GETTER, FIND_SETTER, FIND_STATIC_GETTER, FIND_STATIC_SETTER, FIND_VAR_HANDLE,
			FIND_STATIC_VAR_HANDLE);

	private static final PsiJavaElementPattern.Capture<PsiElement> FIELD_TYPE_ARGUMENT_PATTERN = psiJavaElement().afterLeaf(",").withParent(psiExpression().methodCallParameter(2, methodPattern(ArrayUtil
			.toStringArray(FIELD_HANDLE_FACTORY_NAMES))));


	@Nonnull
	private static PsiMethodPattern methodPattern(String... methodNames)
	{
		return psiMethod().withName(methodNames).definedInClass(JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP);
	}

	@Override
	public void fillCompletionVariants(@Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet result)
	{
		final PsiElement position = parameters.getPosition();
		if(!isInJavaContext(position))
		{
			return;
		}

		if(METHOD_TYPE_ARGUMENT_PATTERN.accepts(position))
		{
			addMethodHandleVariants(position, result);
		}
		else if(FIELD_TYPE_ARGUMENT_PATTERN.accepts(position))
		{
			addFieldHandleVariants(position, result);
		}
	}

	private static void addMethodHandleVariants(@Nonnull PsiElement position, @Nonnull Consumer<LookupElement> result)
	{
		final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
		if(methodCall != null)
		{
			final String methodName = methodCall.getMethodExpression().getReferenceName();
			if(methodName != null && METHOD_HANDLE_FACTORY_NAMES.contains(methodName))
			{
				final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
				final ReflectiveClass reflectiveClass = arguments.length != 0 ? getReflectiveClass(arguments[0]) : null;
				if(reflectiveClass != null)
				{
					switch(methodName)
					{
						case FIND_CONSTRUCTOR:
							addConstructorSignatures(reflectiveClass.getPsiClass(), position, result);
							break;
						case FIND_VIRTUAL:
						case FIND_STATIC:
						case FIND_SPECIAL:
							final String name = arguments.length > 1 ? computeConstantExpression(arguments[1], String.class) : null;
							if(!StringUtil.isEmpty(name))
							{
								addMethodSignatures(reflectiveClass.getPsiClass(), name, FIND_STATIC.equals(methodName), position, result);
							}
							break;
					}
				}
			}
		}
	}

	private static void addConstructorSignatures(@Nonnull PsiClass psiClass, @Nonnull PsiElement context, @Nonnull Consumer<LookupElement> result)
	{
		final String className = psiClass.getName();
		if(className != null)
		{
			final PsiMethod[] constructors = psiClass.getConstructors();
			if(constructors.length != 0)
			{
				lookupMethodTypes(Arrays.stream(constructors), context, result);
			}
			else
			{
				result.consume(lookupSignature(ReflectiveSignature.NO_ARGUMENT_CONSTRUCTOR_SIGNATURE, context));
			}
		}
	}

	private static void addMethodSignatures(@Nonnull PsiClass psiClass, @Nonnull String methodName, boolean isStaticExpected, @Nonnull PsiElement context, @Nonnull Consumer<LookupElement> result)
	{
		final PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
		if(methods.length != 0)
		{
			final Stream<PsiMethod> methodStream = Arrays.stream(methods).filter(method -> method.hasModifierProperty(PsiModifier.STATIC) == isStaticExpected);
			lookupMethodTypes(methodStream, context, result);
		}
	}

	private static void lookupMethodTypes(@Nonnull Stream<PsiMethod> methods, @Nonnull PsiElement context, @Nonnull Consumer<LookupElement> result)
	{
		methods.map(JavaReflectionReferenceUtil::getMethodSignature).filter(Objects::nonNull).sorted(ReflectiveSignature::compareTo).map(signature -> lookupSignature(signature, context)).forEach
				(result::consume);
	}

	@Nonnull
	private static LookupElement lookupSignature(@Nonnull ReflectiveSignature signature, @Nonnull PsiElement context)
	{
		final String expressionText = getMethodTypeExpressionText(signature);
		final PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
		final PsiExpression expression = factory.createExpressionFromText(expressionText, context);

		final String shortTypes = signature.getText(true, type -> PsiNameHelper.getShortClassName(type) + ".class");
		final String presentableText = PsiNameHelper.getShortClassName(JAVA_LANG_INVOKE_METHOD_TYPE) + "." + METHOD_TYPE + shortTypes;
		final String lookupText = METHOD_TYPE + signature.getText(true, PsiNameHelper::getShortClassName);

		return lookupExpression(expression, AllIcons.Nodes.Method, presentableText, lookupText);
	}

	private static void addFieldHandleVariants(@Nonnull PsiElement position, @Nonnull Consumer<LookupElement> result)
	{
		final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
		if(methodCall != null)
		{
			final String methodName = methodCall.getMethodExpression().getReferenceName();
			if(methodName != null && FIELD_HANDLE_FACTORY_NAMES.contains(methodName))
			{
				final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
				if(arguments.length > 2)
				{
					final String fieldName = computeConstantExpression(arguments[1], String.class);
					if(!StringUtil.isEmpty(fieldName))
					{
						final ReflectiveClass reflectiveClass = getReflectiveClass(arguments[0]);
						if(reflectiveClass != null)
						{
							addFieldType(reflectiveClass.getPsiClass(), fieldName, position, result);
						}
					}
				}
			}
		}
	}

	private static void addFieldType(@Nonnull PsiClass psiClass, @Nonnull String fieldName, @Nonnull PsiElement context, @Nonnull Consumer<LookupElement> result)
	{
		final PsiField field = psiClass.findFieldByName(fieldName, false);
		if(field != null)
		{
			final String typeText = getTypeText(field.getType());
			final PsiElementFactory factory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
			final PsiExpression expression = factory.createExpressionFromText(typeText + ".class", context);

			final String shortType = PsiNameHelper.getShortClassName(typeText);
			result.consume(lookupExpression(expression, AllIcons.Nodes.Class, shortType + ".class", shortType));
		}
	}

	@Nonnull
	private static LookupElement lookupExpression(@Nonnull PsiExpression expression, @Nullable Image icon, @Nonnull String presentableText, @Nonnull String lookupText)
	{
		final LookupElement element = new ExpressionLookupItem(expression, icon, presentableText, lookupText)
		{
			@Override
			public void handleInsert(InsertionContext context)
			{
				context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
				context.commitDocument();
				replaceText(context, getObject().getText());
			}
		};
		return PrioritizedLookupElement.withPriority(element, 1);
	}
}
