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
package com.intellij.jam.reflect;

import javax.annotation.Nonnull;

import com.intellij.jam.JamClassGenerator;
import com.intellij.jam.JamElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementRef;
import com.intellij.util.NotNullFunction;

/**
 * @author peter
 */
public abstract class JamInstantiator<Psi extends PsiElement, Jam extends JamElement> {

  @Nonnull
  public abstract Jam instantiate(@Nonnull PsiElementRef<Psi> ref);

  public static <Psi extends PsiElement, Jam extends JamElement> JamInstantiator<Psi, Jam> proxied(final Class<Jam> jamClass) {
    final NotNullFunction<PsiElementRef,Jam> function = JamClassGenerator.getInstance().generateJamElementFactory(jamClass);
    return new JamInstantiator<Psi, Jam>() {
      @Nonnull
      @Override
      public Jam instantiate(@Nonnull PsiElementRef<Psi> psiPsiRef) {
        return function.fun(psiPsiRef);
      }
    };
  }

}
