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
package com.intellij.java.language.impl.psi.impl.smartPointers;

import java.lang.ref.Reference;

import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.SmartTypePointer;
import consulo.util.lang.ref.SoftReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Created by Max Medvedev on 10/25/13
 */
public abstract class TypePointerBase<T extends PsiType> implements SmartTypePointer
{
	private Reference<T> myTypeRef;

	public TypePointerBase(@Nonnull T type)
	{
		myTypeRef = new SoftReference<T>(type);
	}

	@Override
	public T getType()
	{
		T myType = SoftReference.dereference(myTypeRef);
		if(myType != null && myType.isValid())
		{
			return myType;
		}

		myType = calcType();
		myTypeRef = myType == null ? null : new SoftReference<T>(myType);
		return myType;
	}

	@Nullable
	protected abstract T calcType();
}
