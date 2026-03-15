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

import org.jspecify.annotations.Nullable;

import com.intellij.java.language.jvm.types.JvmReferenceType;

public interface JvmClass extends JvmTypeParametersOwner, JvmTypeDeclaration
{

	/**
	 * @return the name, or {@code null} if the class is anonymous
	 * @see Class#getSimpleName
	 */
	@Nullable
	@Override
	String getName();

	/**
	 * @return the qualified name, of {@code null} if the class is anonymous or local
	 * @see Class#getCanonicalName
	 */
	@Nullable
	String getQualifiedName();

	JvmClassKind getClassKind();

	/**
	 * @return direct super type or {@code null} if this class is an interface or represents {@link Object}
	 * @see Class#getSuperclass
	 * @see Class#getGenericSuperclass
	 * @see Class#getAnnotatedSuperclass
	 */
	@Nullable
	JvmReferenceType getSuperClassType();

	/**
	 * @return interface types which are directly implemented by this class
	 * @see Class#getInterfaces
	 * @see Class#getAnnotatedInterfaces
	 * @see Class#getGenericInterfaces
	 */
	JvmReferenceType[] getInterfaceTypes();

	//

	/**
	 * @return all (static, private, etc) methods and constructors declared by this class but excluding inherited ones
	 * @see Class#getDeclaredMethods
	 * @see Class#getDeclaredConstructors
	 */
	JvmMethod[] getMethods();

	/**
	 * @return all (static, private, etc) fields declared by this class but excluding inherited ones
	 * @see Class#getDeclaredFields
	 */
	JvmField[] getFields();

	/**
	 * @return all (static, private, etc) inner classes declared by this class but excluding inherited ones
	 * @see Class#getDeclaredClasses
	 */
	JvmClass[] getInnerClasses();
}
