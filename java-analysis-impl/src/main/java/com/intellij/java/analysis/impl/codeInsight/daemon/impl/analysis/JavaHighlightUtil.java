/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import consulo.java.module.util.JavaClassNames;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaHighlightUtil
{
	public static boolean isSerializable(@Nonnull PsiClass aClass)
	{
		PsiManager manager = aClass.getManager();
		PsiClass serializableClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.io.Serializable", aClass.getResolveScope());
		return serializableClass != null && aClass.isInheritor(serializableClass, true);
	}

	public static boolean isSerializationRelatedMethod(PsiMethod method, PsiClass containingClass)
	{
		if(containingClass == null || method.isConstructor())
		{
			return false;
		}
		if(method.hasModifierProperty(PsiModifier.STATIC))
		{
			return false;
		}
		@NonNls String name = method.getName();
		PsiParameter[] parameters = method.getParameterList().getParameters();
		PsiType returnType = method.getReturnType();
		if("readObjectNoData".equals(name))
		{
			return parameters.length == 0 && TypeConversionUtil.isVoidType(returnType) && isSerializable(containingClass);
		}
		if("readObject".equals(name))
		{
			return parameters.length == 1
					&& parameters[0].getType().equalsToText("java.io.ObjectInputStream")
					&& TypeConversionUtil.isVoidType(returnType) && method.hasModifierProperty(PsiModifier.PRIVATE)
					&& isSerializable(containingClass);
		}
		if("readResolve".equals(name))
		{
			return parameters.length == 0
					&& returnType != null
					&& returnType.equalsToText(JavaClassNames.JAVA_LANG_OBJECT)
					&& (containingClass.hasModifierProperty(PsiModifier.ABSTRACT) || isSerializable(containingClass));
		}
		if("writeReplace".equals(name))
		{
			return parameters.length == 0
					&& returnType != null
					&& returnType.equalsToText(JavaClassNames.JAVA_LANG_OBJECT)
					&& (containingClass.hasModifierProperty(PsiModifier.ABSTRACT) || isSerializable(containingClass));
		}
		if("writeObject".equals(name))
		{
			return parameters.length == 1
					&& TypeConversionUtil.isVoidType(returnType)
					&& parameters[0].getType().equalsToText("java.io.ObjectOutputStream")
					&& method.hasModifierProperty(PsiModifier.PRIVATE)
					&& isSerializable(containingClass);
		}
		return false;
	}

	@Nonnull
	public static String formatType(@javax.annotation.Nullable PsiType type)
	{
		if(type == null)
		{
			return PsiKeyword.NULL;
		}
		String text = type.getInternalCanonicalText();
		return text == null ? PsiKeyword.NULL : text;
	}

	@Nullable
	private static PsiType getArrayInitializerType(@Nonnull final PsiArrayInitializerExpression element)
	{
		final PsiType typeCheckResult = sameType(element.getInitializers());
		if(typeCheckResult != null)
		{
			return typeCheckResult.createArrayType();
		}
		return null;
	}

	@Nullable
	public static PsiType sameType(@Nonnull PsiExpression[] expressions)
	{
		PsiType type = null;
		for(PsiExpression expression : expressions)
		{
			final PsiType currentType;
			if(expression instanceof PsiArrayInitializerExpression)
			{
				currentType = getArrayInitializerType((PsiArrayInitializerExpression) expression);
			}
			else
			{
				currentType = expression.getType();
			}
			if(type == null)
			{
				type = currentType;
			}
			else if(!type.equals(currentType))
			{
				return null;
			}
		}
		return type;
	}

	@Nonnull
	public static String formatMethod(@Nonnull PsiMethod method)
	{
		return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
				PsiFormatUtilBase.SHOW_TYPE);
	}

	public static boolean isSuperOrThisCall(PsiStatement statement, boolean testForSuper, boolean testForThis)
	{
		if(!(statement instanceof PsiExpressionStatement))
		{
			return false;
		}
		PsiExpression expression = ((PsiExpressionStatement) statement).getExpression();
		if(!(expression instanceof PsiMethodCallExpression))
		{
			return false;
		}
		final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) expression).getMethodExpression();
		if(testForSuper)
		{
			if("super".equals(methodExpression.getText()))
			{
				return true;
			}
		}
		if(testForThis)
		{
			if("this".equals(methodExpression.getText()))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * return all constructors which are referred from this constructor by
	 * this (...) at the beginning of the constructor body
	 *
	 * @return referring constructor
	 */
	@Nonnull
	public static List<PsiMethod> getChainedConstructors(@Nonnull PsiMethod constructor)
	{
		final ConstructorVisitorInfo info = new ConstructorVisitorInfo();
		visitConstructorChain(constructor, info);
		if(info.visitedConstructors != null)
		{
			info.visitedConstructors.remove(constructor);
		}
		return ObjectUtils.notNull(info.visitedConstructors, Collections.emptyList());
	}

	static void visitConstructorChain(@Nonnull PsiMethod entry, @Nonnull ConstructorVisitorInfo info)
	{
		PsiMethod constructor = entry;
		while(true)
		{
			PsiMethodCallExpression methodCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
			if(!JavaPsiConstructorUtil.isChainedConstructorCall(methodCall))
			{
				return;
			}
			PsiMethod method = methodCall.resolveMethod();
			if(method == null)
			{
				return;
			}
			if(info.visitedConstructors != null && info.visitedConstructors.contains(method))
			{
				info.recursivelyCalledConstructor = method;
				return;
			}
			if(info.visitedConstructors == null)
			{
				info.visitedConstructors = new ArrayList<>(5);
			}
			info.visitedConstructors.add(method);
			constructor = method;
		}
	}

	static class ConstructorVisitorInfo
	{
		List<PsiMethod> visitedConstructors;
		PsiMethod recursivelyCalledConstructor;
	}
}
