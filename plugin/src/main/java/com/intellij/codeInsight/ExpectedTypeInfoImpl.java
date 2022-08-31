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
package com.intellij.codeInsight;

import javax.annotation.Nonnull;

import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.VolatileNullableLazyValue;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiUtil;

public class ExpectedTypeInfoImpl implements ExpectedTypeInfo
{
	public static final NullableComputable<String> NULL = new NullableComputable<String>()
	{
		@Override
		public String compute()
		{
			return null;
		}
	};
	@Nonnull
	private final PsiType type;
	@Nonnull
	private final PsiType defaultType;
	private final int kind;
	@Nonnull
	private final TailType myTailType;
	private final PsiMethod myCalledMethod;
	@Nonnull
	private final NullableComputable<String> expectedNameComputable;
	@Nonnull
	private final NullableLazyValue<String> expectedNameLazyValue;

	@Override
	public int getKind()
	{
		return kind;
	}

	@Nonnull
	@Override
	public TailType getTailType()
	{
		return myTailType;
	}

	public ExpectedTypeInfoImpl(@Nonnull PsiType type,
			@Type int kind,
			@Nonnull PsiType defaultType,
			@Nonnull TailType myTailType,
			PsiMethod calledMethod,
			@Nonnull NullableComputable<String> expectedName)
	{
		this.type = type;
		this.kind = kind;

		this.myTailType = myTailType;
		this.defaultType = defaultType;
		myCalledMethod = calledMethod;
		this.expectedNameComputable = expectedName;
		expectedNameLazyValue = new VolatileNullableLazyValue<String>()
		{
			@javax.annotation.Nullable
			@Override
			protected String compute()
			{
				return expectedNameComputable.compute();
			}
		};

		PsiUtil.ensureValidType(type);
		PsiUtil.ensureValidType(defaultType);
	}

	@javax.annotation.Nullable
	public String getExpectedName()
	{
		return expectedNameLazyValue.getValue();
	}

	@Override
	public PsiMethod getCalledMethod()
	{
		return myCalledMethod;
	}

	@Override
	@Nonnull
	public PsiType getType()
	{
		return type;
	}

	@Override
	@Nonnull
	public PsiType getDefaultType()
	{
		return defaultType;
	}

	public boolean equals(final Object o)
	{
		if(this == o)
			return true;
		if(!(o instanceof ExpectedTypeInfoImpl))
			return false;

		final ExpectedTypeInfoImpl that = (ExpectedTypeInfoImpl) o;

		if(kind != that.kind)
			return false;
		if(!defaultType.equals(that.defaultType))
			return false;
		if(!myTailType.equals(that.myTailType))
			return false;
		if(!type.equals(that.type))
			return false;

		return true;
	}

	public int hashCode()
	{
		int result = type.hashCode();
		result = 31 * result + defaultType.hashCode();
		result = 31 * result + kind;
		result = 31 * result + myTailType.hashCode();
		return result;
	}

	@Override
	public boolean equals(ExpectedTypeInfo obj)
	{
		return equals((Object) obj);
	}

	@SuppressWarnings({"HardCodedStringLiteral"})
	public String toString()
	{
		return "ExpectedTypeInfo[type='" + type + "' kind='" + kind + "']";
	}

	@Nonnull
	@Override
	public ExpectedTypeInfo[] intersect(@Nonnull ExpectedTypeInfo info)
	{
		ExpectedTypeInfoImpl info1 = (ExpectedTypeInfoImpl) info;

		if(kind == TYPE_STRICTLY)
		{
			if(info1.kind == TYPE_STRICTLY)
			{
				if(info1.type.equals(type))
					return new ExpectedTypeInfoImpl[]{this};
			}
			else
			{
				return info1.intersect(this);
			}
		}
		else if(kind == TYPE_OR_SUBTYPE)
		{
			if(info1.kind == TYPE_STRICTLY)
			{
				if(type.isAssignableFrom(info1.type))
					return new ExpectedTypeInfoImpl[]{info1};
			}
			else if(info1.kind == TYPE_OR_SUBTYPE)
			{
				PsiType otherType = info1.type;
				if(type.isAssignableFrom(otherType))
					return new ExpectedTypeInfoImpl[]{info1};
				else if(otherType.isAssignableFrom(type))
					return new ExpectedTypeInfoImpl[]{this};
			}
			else
			{
				return info1.intersect(this);
			}
		}
		else if(kind == TYPE_OR_SUPERTYPE)
		{
			if(info1.kind == TYPE_STRICTLY)
			{
				if(info1.type.isAssignableFrom(type))
					return new ExpectedTypeInfoImpl[]{info1};
			}
			else if(info1.kind == TYPE_OR_SUBTYPE)
			{
				PsiType otherType = info1.type;
				if(otherType.isAssignableFrom(type))
					return new ExpectedTypeInfoImpl[]{this};
			}
			else if(info1.kind == TYPE_OR_SUPERTYPE)
			{
				PsiType otherType = info1.type;
				if(type.isAssignableFrom(otherType))
					return new ExpectedTypeInfoImpl[]{this};
				else if(otherType.isAssignableFrom(type))
					return new ExpectedTypeInfoImpl[]{info1};
			}
			else
			{
				return info1.intersect(this);
			}
		}


		//todo: the following cases are not implemented: SUPERxSUB, SUBxSUPER

		return ExpectedTypeInfo.EMPTY_ARRAY;
	}
}
