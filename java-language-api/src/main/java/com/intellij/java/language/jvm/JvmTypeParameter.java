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
package com.intellij.java.language.jvm;

import jakarta.annotation.Nullable;
import com.intellij.java.language.jvm.types.JvmReferenceType;
import jakarta.annotation.Nonnull;

import java.lang.reflect.TypeVariable;

/**
 * Represents a type parameter.
 *
 * @see TypeVariable
 */
public interface JvmTypeParameter extends JvmTypeDeclaration
{

	/**
	 * @return bounds of this type parameter
	 * @see TypeVariable#getBounds
	 * @see TypeVariable#getAnnotatedBounds
	 */
	@Nonnull
	JvmReferenceType[] getBounds();

	/**
	 * @return the element which is parameterized by this type parameter
	 * @see TypeVariable#getGenericDeclaration
	 */
	@Nullable
	JvmTypeParametersOwner getOwner();
}
