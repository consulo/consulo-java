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
package com.intellij.java.language.psi;

import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * Data structure which allows efficient retrieval of super methods for a Java method.
 *
 * @author ven
 * @since 5.1
 */
public abstract class HierarchicalMethodSignature extends MethodSignatureBackedByPsiMethod {
  public HierarchicalMethodSignature(@Nonnull MethodSignatureBackedByPsiMethod signature) {
    super(signature.getMethod(), signature.getSubstitutor(), signature.isRaw(), 
          getParameterTypes(signature.getMethod()), signature.getTypeParameters());
  }

  private static PsiType[] getParameterTypes(PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiType[] paramTypes = new PsiType[parameters.length];
    for (int i = 0; i < paramTypes.length; i++) {
      paramTypes[i] = parameters[i].getType();
    }
    return paramTypes;
  }
  
  /**
   * Returns the list of super method signatures for the specified signature.
   *
   * @return the super method signature list.
   * Note that the list may include signatures for which isSubsignature() check returns false, but erasures are equal 
   */
  @Nonnull
  public abstract List<HierarchicalMethodSignature> getSuperSignatures();
}
