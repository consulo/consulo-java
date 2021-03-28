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
package com.intellij.psi;

import static com.intellij.psi.PsiType.getJavaLangObject;
import static com.intellij.psi.PsiType.getTypeByName;

import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nonnull;import javax.annotation.Nullable;

import org.jetbrains.annotations.NonNls;
import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.lang.jvm.types.JvmType;
import consulo.logging.Logger;
import consulo.java.module.util.JavaClassNames;

class PsiJvmConversionHelper
{

	private static final Logger LOG = Logger.getInstance(PsiJvmConversionHelper.class);

	@Nonnull
	static PsiAnnotation[] getListAnnotations(@Nonnull PsiModifierListOwner modifierListOwner)
	{
		PsiModifierList list = modifierListOwner.getModifierList();
		return list == null ? PsiAnnotation.EMPTY_ARRAY : list.getAnnotations();
	}

	@Nonnull
	static JvmModifier[] getListModifiers(@Nonnull PsiModifierListOwner modifierListOwner)
	{
		final Set<JvmModifier> result = EnumSet.noneOf(JvmModifier.class);
		for(@NonNls String modifier : PsiModifier.MODIFIERS)
		{
			if(modifierListOwner.hasModifierProperty(modifier))
			{
				String jvmName = modifier.toUpperCase();
				JvmModifier jvmModifier = JvmModifier.valueOf(jvmName);
				result.add(jvmModifier);
			}
		}
		if(modifierListOwner.hasModifierProperty(PsiModifier.PACKAGE_LOCAL))
		{
			result.add(JvmModifier.PACKAGE_LOCAL);
		}
		return result.toArray(JvmModifier.EMPTY_ARRAY);
	}

	@Nonnull
	static JvmClassKind getJvmClassKind(@Nonnull PsiClass psiClass)
	{
		if(psiClass.isAnnotationType())
		{
			return JvmClassKind.ANNOTATION;
		}
		if(psiClass.isInterface())
		{
			return JvmClassKind.INTERFACE;
		}
		if(psiClass.isEnum())
		{
			return JvmClassKind.ENUM;
		}
		return JvmClassKind.CLASS;
	}

	@javax.annotation.Nullable
	static JvmReferenceType getClassSuperType(@Nonnull PsiClass psiClass)
	{
		if(psiClass.isInterface())
		{
			return null;
		}
		if(psiClass.isEnum())
		{
			return getTypeByName(JavaClassNames.JAVA_LANG_ENUM, psiClass.getProject(), psiClass.getResolveScope());
		}
		if(psiClass instanceof PsiAnonymousClass)
		{
			PsiClassType baseClassType = ((PsiAnonymousClass) psiClass).getBaseClassType();
			PsiClass baseClass = baseClassType.resolve();
			if(baseClass == null || !baseClass.isInterface())
			{
				return baseClassType;
			}
			else
			{
				return getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
			}
		}
		if(JavaClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName()))
		{
			return null;
		}

		PsiClassType[] extendsTypes = psiClass.getExtendsListTypes();
		if(extendsTypes.length != 1)
		{
			return getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
		}
		return extendsTypes[0];
	}

	@Nonnull
	static JvmReferenceType[] getClassInterfaces(@Nonnull PsiClass psiClass)
	{
		if(psiClass instanceof PsiAnonymousClass)
		{
			PsiClassType baseClassType = ((PsiAnonymousClass) psiClass).getBaseClassType();
			PsiClass baseClass = baseClassType.resolve();
			if(baseClass != null && baseClass.isInterface())
			{
				return new JvmReferenceType[]{baseClassType};
			}
			else
			{
				return JvmReferenceType.EMPTY_ARRAY;
			}
		}

		PsiReferenceList referenceList = psiClass.isInterface() ? psiClass.getExtendsList() : psiClass.getImplementsList();
		if(referenceList == null)
		{
			return JvmReferenceType.EMPTY_ARRAY;
		}
		return referenceList.getReferencedTypes();
	}


	@Nullable
	static PsiAnnotation getListAnnotation(@Nonnull PsiModifierListOwner modifierListOwner, @Nonnull String fqn)
	{
		PsiModifierList list = modifierListOwner.getModifierList();
		return list == null ? null : list.findAnnotation(fqn);
	}

	static boolean hasListAnnotation(@Nonnull PsiModifierListOwner modifierListOwner, @Nonnull String fqn)
	{
		PsiModifierList list = modifierListOwner.getModifierList();
		return list != null && list.hasAnnotation(fqn);
	}

	static class PsiJvmSubstitutor implements JvmSubstitutor
	{

		private final
		@Nonnull
		PsiSubstitutor mySubstitutor;

		PsiJvmSubstitutor(@Nonnull PsiSubstitutor substitutor)
		{
			mySubstitutor = substitutor;
		}

		@javax.annotation.Nullable
		@Override
		public JvmType substitute(@Nonnull JvmTypeParameter typeParameter)
		{
			if(!(typeParameter instanceof PsiTypeParameter))
			{
				return null;
			}
			PsiTypeParameter psiTypeParameter = ((PsiTypeParameter) typeParameter);
			return mySubstitutor.substitute(psiTypeParameter);
		}
	}
}
