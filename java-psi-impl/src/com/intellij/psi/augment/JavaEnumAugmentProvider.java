/*
 * Copyright 2013-2015 must-be.org
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

package com.intellij.psi.augment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.java.util.JavaClassNames;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.source.PsiImmediateClassType;

/**
 * @author VISTALL
 * @since 30.04.2015
 */
public class JavaEnumAugmentProvider extends PsiAugmentProvider
{
	public static final Key<Boolean> FLAG = Key.create("enum.method.flags");

	@NotNull
	@Override
	@SuppressWarnings("unchecked")
	public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type)
	{
		if(type == PsiMethod.class && element instanceof PsiClass && element.getUserData(FLAG) == Boolean.TRUE && ((PsiClass) element).isEnum())
		{
			List<Psi> list = new ArrayList<Psi>(2);

			LightMethodBuilder valuesMethod = new LightMethodBuilder(element.getManager(), JavaLanguage.INSTANCE, "values");
			valuesMethod.setContainingClass((PsiClass) element);
			valuesMethod.setMethodReturnType(new PsiArrayType(new PsiImmediateClassType((PsiClass)element, PsiSubstitutor.EMPTY)));
			valuesMethod.addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
			list.add((Psi) valuesMethod);

			LightMethodBuilder valueOfMethod = new LightMethodBuilder(element.getManager(), JavaLanguage.INSTANCE, "valueOf");
			valueOfMethod.setContainingClass((PsiClass) element);
			valueOfMethod.setMethodReturnType(new PsiImmediateClassType((PsiClass) element, PsiSubstitutor.EMPTY));
			valueOfMethod.addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
			valueOfMethod.addParameter("name", JavaClassNames.JAVA_LANG_STRING);
			valueOfMethod.addException(JavaClassNames.JAVA_LANG_ILLEGAL_ARGUMENT_EXCEPTION);
			list.add((Psi) valueOfMethod);
			return list;
		}
		return Collections.emptyList();
	}
}
