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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.java.language.patterns.PsiMethodPattern;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import consulo.java.language.module.util.JavaClassNames;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiMethod;

class PreferMostUsedWeigher extends LookupElementWeigher
{
	private static final PsiMethodPattern OBJECT_METHOD_PATTERN = psiMethod().withName(StandardPatterns.string().oneOf("hashCode", "equals", "finalize", "wait", "notify", "notifyAll", "getClass",
			"clone", "toString")).
			inClass(JavaClassNames.JAVA_LANG_OBJECT);

	private final boolean myConstructorSuggestion;

	private PreferMostUsedWeigher(boolean constructorSuggestion)
	{
		super("mostUsed");
		myConstructorSuggestion = constructorSuggestion;
	}

	// optimization: do not even create weigher if compiler indices aren't available for now
	@Nullable
	static PreferMostUsedWeigher create(@Nonnull PsiElement position)
	{
		return null;
	}

	@Nullable
	@Override
	public Integer weigh(@Nonnull LookupElement element)
	{
		throw new UnsupportedOperationException();
		/*final PsiElement psi = ObjectUtils.tryCast(element.getObject(), PsiElement.class);
		if(!(psi instanceof PsiMember))
		{
			return null;
		}
		if(element.getUserData(JavaGenerateMemberCompletionContributor.GENERATE_ELEMENT) != null)
		{
			return null;
		}
		if(OBJECT_METHOD_PATTERN.accepts(psi))
		{
			return null;
		}
		if(looksLikeHelperMethodOrConst(psi))
		{
			return null;
		}
		final Integer occurrenceCount = myCompilerReferenceService.getCompileTimeOccurrenceCount(psi, myConstructorSuggestion);
		return occurrenceCount == null ? null : -occurrenceCount; */
	}

	//Objects.requireNonNull is an example
	private static boolean looksLikeHelperMethodOrConst(@Nonnull PsiElement element)
	{
		if(!(element instanceof PsiMethod))
		{
			return false;
		}
		PsiMethod method = (PsiMethod) element;
		if(method.isConstructor())
		{
			return false;
		}
		if(isRawDeepTypeEqualToObject(method.getReturnType()))
		{
			return true;
		}
		PsiParameter[] parameters = method.getParameterList().getParameters();
		if(parameters.length == 0)
		{
			return false;
		}
		for(PsiParameter parameter : parameters)
		{
			PsiType paramType = parameter.getType();
			if(isRawDeepTypeEqualToObject(paramType))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isRawDeepTypeEqualToObject(@javax.annotation.Nullable PsiType type)
	{
		if(type == null)
		{
			return false;
		}
		PsiType rawType = TypeConversionUtil.erasure(type.getDeepComponentType());
		if(rawType == null)
		{
			return false;
		}
		return rawType.equalsToText(JavaClassNames.JAVA_LANG_OBJECT);
	}
}
