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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:59 PM
 */
package com.intellij.codeInsight.intention.impl;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;

public class AddNullableNotNullAnnotationFix extends AddAnnotationPsiFix
{
	public AddNullableNotNullAnnotationFix(@Nonnull String fqn, @Nonnull PsiModifierListOwner owner, @Nonnull String... annotationToRemove)
	{
		super(fqn, owner, PsiNameValuePair.EMPTY_ARRAY, annotationToRemove);
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
	{
		if(!super.isAvailable(project, file, startElement, endElement))
		{
			return false;
		}
		PsiModifierListOwner owner = getContainer(file, startElement.getTextRange().getStartOffset());
		if(owner == null || AnnotationUtil.isAnnotated(owner, getAnnotationsToRemove()[0], false, false))
		{
			return false;
		}
		if(owner instanceof PsiMethod)
		{
			PsiType returnType = ((PsiMethod) owner).getReturnType();

			return returnType != null && !(returnType instanceof PsiPrimitiveType);
		}
		return true;
	}
}
