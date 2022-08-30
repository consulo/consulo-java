/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import javax.annotation.Nonnull;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

/**
 * @author peter
 */
public class LightClass extends AbstractLightClass {
  private final PsiClass myDelegate;

  public LightClass(@Nonnull PsiClass delegate) {
    this(delegate, JavaLanguage.INSTANCE);
  }

  public LightClass(@Nonnull PsiClass delegate, final Language language) {
    super(delegate.getManager(), language);
    myDelegate = delegate;
  }

  @Nonnull
  @Override
  public PsiClass getDelegate() {
    return myDelegate;
  }

  @Nonnull
  @Override
  public PsiElement copy() {
    return new LightClass(this);
  }

}
