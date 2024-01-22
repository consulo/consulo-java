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
package com.intellij.java.execution.impl.junit2;

import java.util.Collections;

import jakarta.annotation.Nonnull;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import com.intellij.java.execution.impl.junit.JUnitUtil;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassObjectAccessExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.util.InheritanceUtil;
import jakarta.annotation.Nullable;

public class PsiMemberParameterizedLocation extends PsiLocation<PsiElement>
{
	private final PsiClass myContainingClass;
	private final String myParamSetName;

	public PsiMemberParameterizedLocation(@Nonnull Project project, @Nonnull PsiElement psiElement, @Nullable PsiClass containingClass, String paramSetName)
	{
		super(project, psiElement);
		myContainingClass = containingClass;
		myParamSetName = paramSetName;
	}

	public static Location getParameterizedLocation(PsiClass psiClass, String paramSetName)
	{
		return getParameterizedLocation(psiClass, paramSetName, JUnitUtil.PARAMETERIZED_CLASS_NAME);
	}

	public static Location getParameterizedLocation(PsiClass psiClass, String paramSetName, String parameterizedClassName)
	{
		final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(psiClass, Collections.singleton(JUnitUtil.RUN_WITH));
		if(annotation != null)
		{
			final PsiAnnotationMemberValue attributeValue = annotation.findAttributeValue("value");
			if(attributeValue instanceof PsiClassObjectAccessExpression)
			{
				final PsiTypeElement operand = ((PsiClassObjectAccessExpression) attributeValue).getOperand();
				if(InheritanceUtil.isInheritor(operand.getType(), parameterizedClassName))
				{
					return new PsiMemberParameterizedLocation(psiClass.getProject(), psiClass, null, paramSetName);
				}
			}
		}
		return null;
	}

	public String getParamSetName()
	{
		return myParamSetName;
	}

	public PsiClass getContainingClass()
	{
		return myContainingClass;
	}
}
