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
package com.intellij.codeInsight.completion;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author peter
 */
public class MemberLookupHelper
{
	private final PsiMember myMember;
	private final boolean myMergedOverloads;
	@Nullable
	private final PsiClass myContainingClass;
	private boolean myShouldImport;

	public MemberLookupHelper(List<PsiMethod> overloads, PsiClass containingClass, boolean shouldImport)
	{
		this(overloads.get(0), containingClass, shouldImport, true);
	}

	public MemberLookupHelper(PsiMember member, @javax.annotation.Nullable PsiClass containingClass, boolean shouldImport, final boolean mergedOverloads)
	{
		myMember = member;
		myContainingClass = containingClass;
		myShouldImport = shouldImport;
		myMergedOverloads = mergedOverloads;
	}

	public PsiMember getMember()
	{
		return myMember;
	}

	@javax.annotation.Nullable
	public PsiClass getContainingClass()
	{
		return myContainingClass;
	}

	public void setShouldBeImported(boolean shouldImportStatic)
	{
		myShouldImport = shouldImportStatic;
	}

	public boolean willBeImported()
	{
		return myShouldImport;
	}

	public void renderElement(LookupElementPresentation presentation, boolean showClass, boolean showPackage, PsiSubstitutor substitutor)
	{
		final String className = myContainingClass == null ? "???" : myContainingClass.getName();

		final String memberName = myMember.getName();
		if(showClass && StringUtil.isNotEmpty(className))
		{
			presentation.setItemText(className + "." + memberName);
		}
		else
		{
			presentation.setItemText(memberName);
		}

		final String qname = myContainingClass == null ? "" : myContainingClass.getQualifiedName();
		String pkg = qname == null ? "" : StringUtil.getPackageName(qname);
		String location = showPackage && StringUtil.isNotEmpty(pkg) ? " (" + pkg + ")" : "";

		final String params = myMergedOverloads ? "(...)" : myMember instanceof PsiMethod ? getMethodParameterString((PsiMethod) myMember, substitutor) : "";
		presentation.clearTail();
		presentation.appendTailText(params, false);
		if(myShouldImport && StringUtil.isNotEmpty(className))
		{
			presentation.appendTailText(" in " + className + location, true);
		}
		else
		{
			presentation.appendTailText(location, true);
		}

		PsiType declaredType = myMember instanceof PsiMethod ? ((PsiMethod) myMember).getReturnType() : ((PsiField) myMember).getType();
		PsiType type = patchGetClass(substitutor.substitute(declaredType));
		if(type != null)
		{
			presentation.setTypeText(substitutor.substitute(type).getPresentableText());
		}
	}

	@javax.annotation.Nullable
	private PsiType patchGetClass(@Nullable PsiType type)
	{
		if(myMember instanceof PsiMethod && PsiTypesUtil.isGetClass((PsiMethod) myMember) && type instanceof PsiClassType)
		{
			PsiType arg = ContainerUtil.getFirstItem(Arrays.asList(((PsiClassType) type).getParameters()));
			PsiType bound = arg instanceof PsiWildcardType ? TypeConversionUtil.erasure(((PsiWildcardType) arg).getExtendsBound()) : null;
			if(bound != null)
			{
				return PsiTypesUtil.createJavaLangClassType(myMember, bound, false);
			}
		}
		return type;
	}

	@Nonnull
	static String getMethodParameterString(@Nonnull PsiMethod method, @Nonnull PsiSubstitutor substitutor)
	{
		return PsiFormatUtil.formatMethod(method, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
	}
}
