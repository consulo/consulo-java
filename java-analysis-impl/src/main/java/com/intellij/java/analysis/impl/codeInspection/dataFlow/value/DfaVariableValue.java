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

package com.intellij.java.analysis.impl.codeInspection.dataFlow.value;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.Mutability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.SpecialField;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.language.psi.*;
import consulo.util.lang.Pair;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.util.collection.SmartList;
import org.jetbrains.annotations.Contract;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DfaVariableValue extends DfaValue
{

	public static class Factory
	{
		private final Map<Pair<VariableDescriptor, DfaVariableValue>, DfaVariableValue> myExistingVars = new HashMap<>();
		private final DfaValueFactory myFactory;

		Factory(DfaValueFactory factory)
		{
			myFactory = factory;
		}

		@Nonnull
		public DfaVariableValue createVariableValue(PsiVariable variable)
		{
			DfaVariableValue qualifier = null;
			if(variable instanceof PsiField && !(variable.hasModifierProperty(PsiModifier.STATIC)))
			{
				qualifier = createThisValue(((PsiField) variable).getContainingClass());
			}
			return createVariableValue(new DfaExpressionFactory.PlainDescriptor(variable), qualifier);
		}

		/**
		 * Creates a variable representing "this" value with given class as a context
		 *
		 * @param aClass a class to bind "this" value to
		 * @return a DFA variable
		 */
		@Contract("null -> null; !null -> !null")
		public DfaVariableValue createThisValue(@Nullable PsiClass aClass)
		{
			if(aClass == null)
			{
				return null;
			}
			return createVariableValue(new DfaExpressionFactory.ThisDescriptor(aClass));
		}

		@Nonnull
		public DfaVariableValue createVariableValue(@Nonnull VariableDescriptor descriptor)
		{
			return createVariableValue(descriptor, null);
		}

		@Nonnull
		DfaVariableValue createVariableValue(@Nonnull VariableDescriptor descriptor, @Nullable DfaVariableValue qualifier)
		{
			Pair<VariableDescriptor, DfaVariableValue> key = Pair.create(descriptor, qualifier);
			DfaVariableValue var = myExistingVars.get(key);
			if(var == null)
			{
				var = new DfaVariableValue(descriptor, myFactory, qualifier);
				myExistingVars.put(key, var);
				while(qualifier != null)
				{
					qualifier.myDependents.add(var);
					qualifier = qualifier.getQualifier();
				}
			}
			return var;
		}
	}

	@Nonnull
	private final VariableDescriptor myDescriptor;
	private final PsiType myVarType;
	@Nullable
	private final DfaVariableValue myQualifier;
	private DfType myInherentType;
	private final List<DfaVariableValue> myDependents = new SmartList<>();

	private DfaVariableValue(@Nonnull VariableDescriptor descriptor, @Nonnull DfaValueFactory factory, @Nullable DfaVariableValue qualifier)
	{
		super(factory);
		myDescriptor = descriptor;
		myQualifier = qualifier;
		myVarType = descriptor.getType(qualifier);
		if(myDescriptor instanceof DfaExpressionFactory.AssertionDisabledDescriptor)
		{
			myFactory.setAssertionDisabled(this);
		}
	}

	@Nullable
	public PsiModifierListOwner getPsiVariable()
	{
		return myDescriptor.getPsiElement();
	}

	@Nonnull
	public VariableDescriptor getDescriptor()
	{
		return myDescriptor;
	}

	@Override
	@Nullable
	public PsiType getType()
	{
		return myVarType;
	}

	@Override
	public boolean dependsOn(DfaVariableValue other)
	{
		return other == this || (myQualifier != null && myQualifier.dependsOn(other));
	}

	/**
	 * @return list of all variables created within the same factory which are directly or indirectly qualified by this variable.
	 */
	@Nonnull
	public List<DfaVariableValue> getDependentVariables()
	{
		return myDependents;
	}

	public int getDepth()
	{
		int depth = 0;
		DfaVariableValue qualifier = getQualifier();
		while(qualifier != null)
		{
			depth++;
			qualifier = qualifier.getQualifier();
		}
		return depth;
	}

	@Nonnull
	@Contract(pure = true)
	public DfaVariableValue withQualifier(DfaVariableValue newQualifier)
	{
		return newQualifier == myQualifier ? this : myFactory.getVarFactory().createVariableValue(myDescriptor, newQualifier);
	}

	public String toString()
	{
		return (myQualifier == null ? "" : myQualifier + ".") + myDescriptor;
	}

	@Nullable
	public DfaVariableValue getQualifier()
	{
		return myQualifier;
	}

	public DfType getInherentType()
	{
		if(myInherentType == null)
		{
			myInherentType = calcInherentType();
		}
		return myInherentType;
	}

	private DfType calcInherentType()
	{
		PsiType type = getType();
		DfType dfType = DfTypes.typedObject(type, Nullability.UNKNOWN);
		if(myDescriptor instanceof SpecialField)
		{
			return dfType.meet(((SpecialField) myDescriptor).getDefaultValue(false));
		}
		PsiModifierListOwner psi = getPsiVariable();
		if(type instanceof PsiPrimitiveType)
		{
			if(TypeConversionUtil.isIntegralNumberType(type))
			{
				LongRangeSet fromType = LongRangeSet.fromType(type);
				if(fromType != null)
				{
					LongRangeSet range = LongRangeSet.fromPsiElement(psi).intersect(fromType);
					return type.equals(PsiType.LONG) ? DfTypes.longRange(range) : DfTypes.intRange(range);
				}
			}
		}
		if(dfType instanceof DfReferenceType)
		{
			if(psi != null)
			{
				dfType = dfType.meet(Mutability.getMutability(psi).asDfType());
			}
			dfType = dfType.meet(NullabilityUtil.calcCanBeNull(this).asDfType());
		}
		return dfType;
	}

	@Nonnull
	public Nullability getInherentNullability()
	{
		return DfaNullability.toNullability(DfaNullability.fromDfType(getInherentType()));
	}

	public boolean isFlushableByCalls()
	{
		return !myDescriptor.isStable() || (myQualifier != null && myQualifier.isFlushableByCalls());
	}

	public boolean containsCalls()
	{
		return myDescriptor.isCall() || myQualifier != null && myQualifier.containsCalls();
	}
}
