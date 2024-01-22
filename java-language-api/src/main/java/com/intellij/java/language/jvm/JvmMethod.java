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

import com.intellij.java.language.jvm.types.JvmReferenceType;
import com.intellij.java.language.jvm.types.JvmType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

/**
 * Represents a method or a constructor.
 *
 * @see Method
 * @see Constructor
 * @see Executable
 */
public interface JvmMethod extends JvmTypeParametersOwner {

  /**
   * @return {@code true} if this method is a constructor
   */
  boolean isConstructor();

  /**
   * @see Executable#getName
   */
  @Nonnull
  @Override
  String getName();

  /**
   * @return return type of a method or {@code null} if this {@code JvmMethod} represents a constructor
   * @see Method#getGenericReturnType
   * @see Method#getAnnotatedReturnType
   */
  @Nullable
  JvmType getReturnType();

  /**
   * @see Executable#getParameters
   */
  @Nonnull
  JvmParameter[] getParameters();

  /**
   * @see Executable#isVarArgs
   */
  boolean isVarArgs();

  /**
   * @see Method#getGenericExceptionTypes
   * @see Method#getAnnotatedExceptionTypes
   */
  @jakarta.annotation.Nonnull
  JvmReferenceType[] getThrowsTypes();
}
