// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.value;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.SpecialField;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.language.psi.PsiType;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

public final class DfaBoxedValue extends DfaValue
{
	private final
	@Nonnull
	DfaVariableValue myWrappedValue;
	private final
	@Nullable
	PsiType myType;

	private DfaBoxedValue(@Nonnull DfaVariableValue valueToWrap, @Nonnull DfaValueFactory factory, @Nullable PsiType type)
	{
		super(factory);
		myWrappedValue = valueToWrap;
		myType = type;
	}

	@NonNls
	public String toString()
	{
		return "Boxed " + myWrappedValue.toString();
	}

	@Nonnull
	public DfaVariableValue getWrappedValue()
	{
		return myWrappedValue;
	}

	@Nullable
	@Override
	public PsiType getType()
	{
		return myType;
	}

	@Nonnull
	@Override
	public DfType getDfType()
	{
		return DfTypes.typedObject(myType, Nullability.NOT_NULL);
	}

	public static class Factory
	{
		private final IntObjectMap<DfaBoxedValue> cachedValues = IntMaps.newIntObjectHashMap();

		private final DfaValueFactory myFactory;

		public Factory(DfaValueFactory factory)
		{
			myFactory = factory;
		}

		@Nullable
		public DfaValue createBoxed(DfaValue valueToWrap, @Nullable PsiType type)
		{
			if(valueToWrap instanceof DfaVariableValue && ((DfaVariableValue) valueToWrap).getDescriptor() == SpecialField.UNBOX)
			{
				DfaVariableValue qualifier = ((DfaVariableValue) valueToWrap).getQualifier();
				if(qualifier != null && (type == null || type.equals(qualifier.getType())))
				{
					return qualifier;
				}
			}
			if(valueToWrap instanceof DfaTypeValue)
			{
				DfType dfType = SpecialField.UNBOX.asDfType(valueToWrap.getDfType(), type);
				return myFactory.fromDfType(dfType);
			}
			if(valueToWrap instanceof DfaVariableValue)
			{
				int id = valueToWrap.getID();
				DfaBoxedValue boxedValue = cachedValues.get(id);
				if(boxedValue == null)
				{
					cachedValues.put(id, boxedValue = new DfaBoxedValue((DfaVariableValue) valueToWrap, myFactory, type));
				}
				return boxedValue;
			}
			return null;
		}
	}
}
