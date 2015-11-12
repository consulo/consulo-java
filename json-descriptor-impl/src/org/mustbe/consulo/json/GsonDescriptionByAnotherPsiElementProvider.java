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

package org.mustbe.consulo.json;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.RequiredDispatchThread;
import org.mustbe.consulo.RequiredReadAction;
import org.mustbe.consulo.java.module.extension.JavaModuleExtension;
import org.mustbe.consulo.java.util.JavaClassNames;
import org.mustbe.consulo.json.validation.descriptionByAnotherPsiElement.DescriptionByAnotherPsiElementProvider;
import org.mustbe.consulo.json.validation.descriptor.JsonObjectDescriptor;
import org.mustbe.consulo.json.validation.descriptor.JsonPropertyDescriptor;
import org.mustbe.consulo.module.extension.ModuleExtensionHelper;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author VISTALL
 * @since 12.11.2015
 */
public class GsonDescriptionByAnotherPsiElementProvider implements DescriptionByAnotherPsiElementProvider<PsiClass>
{
	@NotNull
	@Override
	public String getId()
	{
		return "GSON";
	}

	@NotNull
	@Override
	public String getPsiElementName()
	{
		return "Class";
	}

	@RequiredReadAction
	@NotNull
	@Override
	public String getIdFromPsiElement(@NotNull PsiClass psiClass)
	{
		return psiClass.getQualifiedName();
	}

	@RequiredReadAction
	@Nullable
	@Override
	public PsiClass getPsiElementById(@NotNull String s, @NotNull Project project)
	{
		return JavaPsiFacade.getInstance(project).findClass(s, GlobalSearchScope.allScope(project));
	}

	@RequiredDispatchThread
	@Nullable
	@Override
	public PsiClass chooseElement(@NotNull Project project)
	{
		TreeClassChooser classChooser = TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser("Choose class");
		classChooser.showDialog();
		return classChooser.getSelected();
	}

	@RequiredReadAction
	@Override
	public boolean isAvailable(@NotNull Project project)
	{
		return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class) && getPsiElementById("com.google.gson.Gson", project) != null;
	}

	@Override
	public void fillRootObject(@NotNull PsiClass psiClass, @NotNull JsonObjectDescriptor jsonObjectDescriptor)
	{
		Object type = toType(psiClass.getProject(), new PsiImmediateClassType(psiClass, PsiSubstitutor.EMPTY));

		if(type instanceof JsonObjectDescriptor)
		{
			for(Map.Entry<String, JsonPropertyDescriptor> entry : ((JsonObjectDescriptor) type).getProperties().entrySet())
			{
				jsonObjectDescriptor.getProperties().put(entry.getKey(), entry.getValue());
			}
		}
	}

	@Nullable
	private static Object toType(@NotNull Project project, @NotNull PsiType type)
	{
		if(PsiType.BYTE.equals(type))
		{
			return Number.class;
		}
		else if(PsiType.SHORT.equals(type))
		{
			return Number.class;
		}
		else if(PsiType.INT.equals(type))
		{
			return Number.class;
		}
		else if(PsiType.LONG.equals(type))
		{
			return Number.class;
		}
		else if(PsiType.FLOAT.equals(type))
		{
			return Number.class;
		}
		else if(PsiType.DOUBLE.equals(type))
		{
			return Number.class;
		}
		else if(PsiType.BOOLEAN.equals(type))
		{
			return Boolean.class;
		}
		else if(type instanceof PsiClassType)
		{
			PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType) type).resolveGenerics();
			PsiClass psiClass = classResolveResult.getElement();
			if(psiClass != null)
			{
				String qualifiedName = psiClass.getQualifiedName();
				if(JavaClassNames.JAVA_LANG_STRING.equals(qualifiedName))
				{
					return String.class;
				}
				else if(JavaClassNames.JAVA_LANG_BOOLEAN.equals(qualifiedName) || "java.util.concurrent.atomic.AtomicBoolean".equals(qualifiedName))
				{
					return Boolean.class;
				}
				else if(JavaClassNames.JAVA_LANG_BYTE.equals(qualifiedName))
				{
					return Number.class;
				}
				else if(JavaClassNames.JAVA_LANG_SHORT.equals(qualifiedName))
				{
					return Number.class;
				}
				else if(JavaClassNames.JAVA_LANG_INTEGER.equals(qualifiedName) || "java.util.concurrent.atomic.AtomicInteger".equals(qualifiedName))
				{
					return Number.class;
				}
				else if(JavaClassNames.JAVA_LANG_LONG.equals(qualifiedName) || "java.util.concurrent.atomic.AtomicLong".equals(qualifiedName))
				{
					return Number.class;
				}
				else if(JavaClassNames.JAVA_LANG_FLOAT.equals(qualifiedName))
				{
					return Number.class;
				}
				else if(JavaClassNames.JAVA_LANG_DOUBLE.equals(qualifiedName) || "java.util.concurrent.atomic.AtomicDouble".equals(qualifiedName))
				{
					return Number.class;
				}
				else if("java.util.concurrent.atomic.AtomicIntegerArray".equals(qualifiedName))
				{
					return Number[].class;
				}
				else if("java.util.concurrent.atomic.AtomicLongArray".equals(qualifiedName))
				{
					return Number[].class;
				}
				else if("java.util.concurrent.atomic.AtomicDoubleArray".equals(qualifiedName))
				{
					return Number[].class;
				}

				PsiClass collectionClass = JavaPsiFacade.getInstance(project).findClass(JavaClassNames.JAVA_UTIL_COLLECTION, GlobalSearchScope.allScope(project));
				if(collectionClass != null)
				{
					if(InheritanceUtil.isInheritorOrSelf(psiClass, collectionClass, true))
					{
						PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(collectionClass, psiClass, classResolveResult.getSubstitutor());
						Collection<PsiType> values = superClassSubstitutor.getSubstitutionMap().values();
						if(!values.isEmpty())
						{
							PsiType firstItem = ContainerUtil.getFirstItem(values);
							assert firstItem != null;
							return toType(project, new PsiArrayType(firstItem));
						}

						return Object[].class;
					}
				}

				PsiClass mapClass = JavaPsiFacade.getInstance(project).findClass(JavaClassNames.JAVA_UTIL_MAP, GlobalSearchScope.allScope(project));
				if(mapClass != null)
				{
					if(InheritanceUtil.isInheritorOrSelf(psiClass, mapClass, true))
					{
						PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(mapClass, psiClass, classResolveResult.getSubstitutor());
						Collection<PsiType> values = superClassSubstitutor.getSubstitutionMap().values();
						if(values.size() == 2)
						{
							PsiTypeParameter psiTypeParameter = mapClass.getTypeParameters()[1];
							PsiType valueType = superClassSubstitutor.substitute(psiTypeParameter);
							assert valueType != null;

							JsonObjectDescriptor objectDescriptor = new JsonObjectDescriptor();
							Object valueJsonType = toType(project, valueType);
							addIfNotNull(objectDescriptor, valueJsonType, null);
							return objectDescriptor;
						}

						return Object[].class;
					}
				}

				JsonObjectDescriptor objectDescriptor = new JsonObjectDescriptor();
				PsiField[] allFields = psiClass.getAllFields();
				for(PsiField psiField : allFields)
				{
					if(psiField.hasModifierProperty(PsiModifier.STATIC))
					{
						continue;
					}
					Object classType = toType(project, psiField.getType());

					addIfNotNull(objectDescriptor, classType, psiField.getName());
				}

				return objectDescriptor;
			}
		}
		else if(type instanceof PsiArrayType)
		{
			PsiType componentType = ((PsiArrayType) type).getComponentType();

			Object aClass = toType(project, componentType);
			if(!(aClass instanceof Class))
			{
				return null;
			}
			Object o = Array.newInstance((Class<?>) aClass, 0);
			return o.getClass();
		}
		return null;
	}

	private static void addIfNotNull(@NotNull JsonObjectDescriptor objectDescriptor, @Nullable Object classType, @Nullable String fieldName)
	{
		if(classType instanceof Class)
		{
			objectDescriptor.addProperty(fieldName, (Class<?>) classType);
		}
		else if(classType instanceof JsonObjectDescriptor)
		{
			objectDescriptor.addProperty(fieldName, (JsonObjectDescriptor) classType);
		}
	}
}
