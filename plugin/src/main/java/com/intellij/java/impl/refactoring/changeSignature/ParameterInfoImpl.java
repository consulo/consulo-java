// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.impl.refactoring.changeSignature;

import com.intellij.java.language.psi.*;
import consulo.util.lang.StringUtil;
import consulo.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ParameterInfoImpl implements JavaParameterInfo
{
	private static final Logger LOG = Logger.getInstance(ParameterInfoImpl.class);

	public int oldParameterIndex;
	private boolean useAnySingleVariable;
	private String name = "";

	private CanonicalTypes.Type myType;
	String defaultValue = "";

	/**
	 * @see #create(int)
	 * @see #createNew()
	 */
	public ParameterInfoImpl(int oldParameterIndex)
	{
		this.oldParameterIndex = oldParameterIndex;
	}

	/**
	 * @see #create(int)
	 * @see #createNew()
	 * @see #withName(String)
	 * @see #withType(PsiType)
	 */
	public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType)
	{
		setName(name);
		this.oldParameterIndex = oldParameterIndex;
		setType(aType);
	}

	/**
	 * @see #create(int)
	 * @see #createNew()
	 * @see #withName(String)
	 * @see #withType(PsiType)
	 * @see #withDefaultValue(String)
	 */
	public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType, @NonNls String defaultValue)
	{
		this(oldParameterIndex, name, aType, defaultValue, false);
	}

	/**
	 * @see #create(int)
	 * @see #createNew()
	 * @see #withName(String)
	 * @see #withType(PsiType)
	 * @see #withDefaultValue(String)
	 * @see #useAnySingleVariable()
	 */
	public ParameterInfoImpl(int oldParameterIndex, @NonNls String name, PsiType aType, @NonNls String defaultValue, boolean useAnyVariable)
	{
		this(oldParameterIndex, name, aType);
		this.defaultValue = defaultValue;
		useAnySingleVariable = useAnyVariable;
	}

	/**
	 * @see #create(int)
	 * @see #createNew()
	 * @see #withName(String)
	 * @see #withType(CanonicalTypes.Type)
	 * @see #withDefaultValue(String)
	 */
	public ParameterInfoImpl(int oldParameterIndex, String name, CanonicalTypes.Type typeWrapper, String defaultValue)
	{
		setName(name);
		this.oldParameterIndex = oldParameterIndex;
		myType = typeWrapper;
		this.defaultValue = defaultValue;
	}

	@Override
	public int getOldIndex()
	{
		return oldParameterIndex;
	}

	@Override
	public void setUseAnySingleVariable(boolean useAnySingleVariable)
	{
		this.useAnySingleVariable = useAnySingleVariable;
	}

	public void updateFromMethod(PsiMethod method)
	{
		if(getTypeWrapper() != null)
		{
			return;
		}
		final PsiParameter[] parameters = method.getParameterList().getParameters();
		LOG.assertTrue(oldParameterIndex >= 0 && oldParameterIndex < parameters.length);
		final PsiParameter parameter = parameters[oldParameterIndex];
		setName(parameter.getName());
		setType(parameter.getType());
	}

	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(!(o instanceof ParameterInfoImpl))
		{
			return false;
		}

		ParameterInfoImpl parameterInfo = (ParameterInfoImpl) o;

		if(oldParameterIndex != parameterInfo.oldParameterIndex)
		{
			return false;
		}
		if(defaultValue != null ? !defaultValue.equals(parameterInfo.defaultValue) : parameterInfo.defaultValue != null)
		{
			return false;
		}
		if(!getName().equals(parameterInfo.getName()))
		{
			return false;
		}
		return getTypeText().equals(parameterInfo.getTypeText());
	}

	public int hashCode()
	{
		final String name = getName();
		int result = name != null ? name.hashCode() : 0;
		result = 29 * result + getTypeText().hashCode();
		return result;
	}

	@Override
	public String getTypeText()
	{
		return getTypeWrapper() == null ? "" : getTypeWrapper().getTypeText();
	}

	@Override
	public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException
	{
		return getTypeWrapper() == null ? null : getTypeWrapper().getType(context, manager);
	}

	@Override
	public void setType(PsiType type)
	{
		myType = CanonicalTypes.createTypeWrapper(type);
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public CanonicalTypes.Type getTypeWrapper()
	{
		return myType;
	}

	@Override
	public void setName(String name)
	{
		this.name = name != null ? name : "";
	}

	@Override
	public boolean isVarargType()
	{
		return getTypeText().endsWith("...");
	}

	@Override
	@Nullable
	public PsiExpression getValue(final PsiCallExpression expr) throws IncorrectOperationException
	{
		if(StringUtil.isEmpty(defaultValue))
		{
			return null;
		}
		final PsiExpression expression =
				JavaPsiFacade.getElementFactory(expr.getProject()).createExpressionFromText(defaultValue, expr);
		return (PsiExpression) JavaCodeStyleManager.getInstance(expr.getProject()).shortenClassReferences(expression);
	}

	@Override
	public boolean isUseAnySingleVariable()
	{
		return useAnySingleVariable;
	}

	@Override
	public String getDefaultValue()
	{
		return defaultValue;
	}

	public void setDefaultValue(final String defaultValue)
	{
		this.defaultValue = defaultValue;
	}

	/**
	 * Returns an array of {@code ParameterInfoImpl} entries which correspond to given method signature.
	 *
	 * @param method method to create an array from
	 * @return an array of ParameterInfoImpl entries
	 */
	@Nonnull
	public static ParameterInfoImpl[] fromMethod(@Nonnull PsiMethod method)
	{
		List<ParameterInfoImpl> result = new ArrayList<>();
		final PsiParameter[] parameters = method.getParameterList().getParameters();
		for(int i = 0; i < parameters.length; i++)
		{
			PsiParameter parameter = parameters[i];
			result.add(create(i).withName(parameter.getName()).withType(parameter.getType()));
		}
		return result.toArray(new ParameterInfoImpl[0]);
	}

	/**
	 * Returns an array of {@code ParameterInfoImpl} entries which correspond to given method signature with given parameter removed.
	 *
	 * @param method            method to create an array from
	 * @param parameterToRemove parameter to remove from method signature
	 * @return an array of ParameterInfoImpl entries
	 */
	public static ParameterInfoImpl[] fromMethodExceptParameter(@Nonnull PsiMethod method, @Nonnull PsiParameter parameterToRemove)
	{
		List<ParameterInfoImpl> result = new ArrayList<>();
		PsiParameter[] parameters = method.getParameterList().getParameters();
		for(int i = 0; i < parameters.length; i++)
		{
			PsiParameter parameter = parameters[i];
			if(!parameterToRemove.equals(parameter))
			{
				result.add(create(i).withName(parameter.getName()).withType(parameter.getType()));
			}
		}
		return result.toArray(new ParameterInfoImpl[0]);
	}

	@Nonnull
	@Contract(value = "-> new", pure = true)
	public static ParameterInfoImpl createNew()
	{
		return create(-1);
	}

	@Nonnull
	@Contract(value = "_ -> new", pure = true)
	public static ParameterInfoImpl create(int oldParameterIndex)
	{
		return new ParameterInfoImpl(oldParameterIndex);
	}

	@Nonnull
	@Contract(value = "_ -> this")
	public ParameterInfoImpl withName(@NonNls String name)
	{
		setName(name);
		return this;
	}

	@Nonnull
	@Contract(value = "_ -> this")
	public ParameterInfoImpl withType(PsiType aType)
	{
		setType(aType);
		return this;
	}

	@Nonnull
	@Contract(value = "_ -> this")
	public ParameterInfoImpl withType(CanonicalTypes.Type typeWrapper)
	{
		myType = typeWrapper;
		return this;
	}

	@Nonnull
	@Contract(value = "_ -> this")
	public ParameterInfoImpl withDefaultValue(@NonNls String defaultValue)
	{
		this.defaultValue = defaultValue;
		return this;
	}

	@Nonnull
	@Contract(value = "-> this")
	public ParameterInfoImpl useAnySingleVariable()
	{
		useAnySingleVariable = true;
		return this;
	}
}
