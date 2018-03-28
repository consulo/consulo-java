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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author Pavel.Dolgov
 */
public class AddAnnotationAttributeNameFix extends LocalQuickFixAndIntentionActionOnPsiElement
{
	private final String myName;

	public AddAnnotationAttributeNameFix(PsiNameValuePair pair, String name)
	{
		super(pair);
		myName = name;
	}

	@Nls
	@Nonnull
	@Override
	public String getText()
	{
		return "Add '" + myName + "='";
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return "Add annotation attribute name";
	}

	@Override
	public void invoke(@Nonnull Project project,
			@Nonnull PsiFile file,
			@javax.annotation.Nullable Editor editor,
			@Nonnull PsiElement startElement,
			@Nonnull PsiElement endElement)
	{
		doFix((PsiNameValuePair) startElement, myName);
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
	{
		return super.isAvailable(project, file, startElement, endElement) && startElement instanceof PsiNameValuePair;
	}

	@Nonnull
	public static List<IntentionAction> createFixes(@Nonnull PsiNameValuePair pair)
	{
		final PsiAnnotationMemberValue value = pair.getValue();
		if(value == null || pair.getName() != null)
		{
			return Collections.emptyList();
		}

		final Collection<String> methodNames = getAvailableAnnotationMethodNames(pair);
		return ContainerUtil.map2List(methodNames, name -> new AddAnnotationAttributeNameFix(pair, name));
	}

	public static void doFix(@Nonnull PsiNameValuePair annotationParameter, @Nonnull String name)
	{
		final String text = buildReplacementText(annotationParameter, name);
		final PsiElementFactory factory = JavaPsiFacade.getElementFactory(annotationParameter.getProject());
		final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@A(" + text + " )", annotationParameter);
		annotationParameter.replace(newAnnotation.getParameterList().getAttributes()[0]);
	}

	private static String buildReplacementText(@Nonnull PsiNameValuePair annotationParameter, @Nonnull String name)
	{
		final PsiAnnotationMemberValue value = annotationParameter.getValue();
		return value != null ? name + "=" + value.getText() : name + "=";
	}

	public static boolean isCompatibleReturnType(@Nonnull PsiMethod psiMethod, @javax.annotation.Nullable PsiType valueType)
	{
		final PsiType expectedType = psiMethod.getReturnType();
		if(expectedType == null || valueType == null || expectedType.isAssignableFrom(valueType))
		{
			return true;
		}
		if(expectedType instanceof PsiArrayType)
		{
			final PsiType componentType = ((PsiArrayType) expectedType).getComponentType();
			return componentType.isAssignableFrom(valueType);
		}
		return false;
	}

	@Nonnull
	private static Collection<String> getAvailableAnnotationMethodNames(@Nonnull PsiNameValuePair pair)
	{
		final PsiAnnotationMemberValue value = pair.getValue();
		if(value != null && pair.getName() == null)
		{
			final PsiElement parent = pair.getParent();
			if((parent instanceof PsiAnnotationParameterList))
			{
				final PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList) parent;
				final PsiClass annotationClass = getAnnotationClass(parameterList);

				if(annotationClass != null)
				{
					final Set<String> usedNames = getUsedAttributeNames(parameterList);

					final Collection<PsiMethod> availableMethods = Arrays.stream(annotationClass.getMethods()).filter(PsiAnnotationMethod.class::isInstance).filter(psiMethod -> !usedNames.contains
							(psiMethod.getName())).collect(Collectors.toList());

					if(!availableMethods.isEmpty())
					{
						final PsiType valueType = CreateAnnotationMethodFromUsageFix.getAnnotationValueType(value);
						return availableMethods.stream().filter(psiMethod -> isCompatibleReturnType(psiMethod, valueType)).map(PsiMethod::getName).collect(Collectors.toSet());
					}
				}
			}
		}
		return Collections.emptyList();
	}

	@Nonnull
	public static Set<String> getUsedAttributeNames(@Nonnull PsiAnnotationParameterList parameterList)
	{
		return Arrays.stream(parameterList.getAttributes()).map(PsiNameValuePair::getName).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	@javax.annotation.Nullable
	private static PsiClass getAnnotationClass(@Nonnull PsiAnnotationParameterList parameterList)
	{
		final PsiElement parent = parameterList.getParent();
		if(parent instanceof PsiAnnotation)
		{
			final PsiJavaCodeReferenceElement reference = ((PsiAnnotation) parent).getNameReferenceElement();
			if(reference != null)
			{
				final PsiElement resolved = reference.resolve();
				if(resolved instanceof PsiClass && ((PsiClass) resolved).isAnnotationType())
				{
					return (PsiClass) resolved;
				}
			}
		}
		return null;
	}
}
