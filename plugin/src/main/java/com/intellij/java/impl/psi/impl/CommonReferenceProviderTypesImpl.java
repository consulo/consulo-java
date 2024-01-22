/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl;

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.language.impl.psi.CommonReferenceProviderTypes;
import com.intellij.java.language.impl.psi.JavaClassPsiReferenceProvider;
import consulo.annotation.component.ServiceImpl;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

/**
 * @author Dmitry Avdeev
 */
@Singleton
@ServiceImpl
public class CommonReferenceProviderTypesImpl extends CommonReferenceProviderTypes {

  private final JavaClassReferenceProvider myProvider;
  private final JavaClassReferenceProvider mySoftProvider;

  public CommonReferenceProviderTypesImpl() {
    myProvider = new JavaClassReferenceProvider();
    mySoftProvider = new JavaClassReferenceProvider() {
      @Override
      public boolean isSoft() {
        return true;
      }
    };
  }

  @Nonnull
  @Override
  public JavaClassPsiReferenceProvider getClassReferenceProvider() {
    return myProvider;
  }

  @Nonnull
  @Override
  public JavaClassPsiReferenceProvider getSoftClassReferenceProvider() {
    return mySoftProvider;
  }
}
