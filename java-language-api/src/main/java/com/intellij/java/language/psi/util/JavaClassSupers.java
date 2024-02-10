/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.language.psi.util;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.psi.scope.GlobalSearchScope;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author peter
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class JavaClassSupers {
  public static JavaClassSupers getInstance() {
    return ServiceManager.getService(JavaClassSupers.class);
  }

  /**
   * Calculates substitutor that binds type parameters in <code>superClass</code> with
   * values that they have in <code>derivedClass</code>, given that type parameters in
   * <code>derivedClass</code> are bound by <code>derivedSubstitutor</code>.
   *
   * @return substitutor or <code>null</code>, if <code>derivedClass</code> doesn't inherit <code>superClass</code>
   * @see PsiClass#isInheritor(PsiClass, boolean)
   * @see InheritanceUtil#isInheritorOrSelf(PsiClass, PsiClass, boolean)
   */
  @Nullable
  public abstract PsiSubstitutor getSuperClassSubstitutor(@Nonnull PsiClass superClass,
                                                          @Nonnull PsiClass derivedClass,
                                                          @Nonnull GlobalSearchScope resolveScope,
                                                          @Nonnull PsiSubstitutor derivedSubstitutor);

}
