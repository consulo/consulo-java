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
package com.intellij.java.language.psi.util;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.logging.Logger;

public class MethodSignatureBackedByPsiMethod extends MethodSignatureBase {
  private static final Logger LOG = Logger.getInstance(MethodSignatureBackedByPsiMethod.class);

  private final PsiMethod myMethod;
  private final boolean myIsRaw;

  protected MethodSignatureBackedByPsiMethod(@Nonnull PsiMethod method,
                                             @Nonnull PsiSubstitutor substitutor,
                                             boolean isRaw,
                                             @Nonnull PsiType[] parameterTypes,
                                             @Nonnull PsiTypeParameter[] methodTypeParameters) {
    super(substitutor, parameterTypes, methodTypeParameters);
    myIsRaw = isRaw;
    if (!method.isValid()) {
      LOG.error("Invalid method: "+method, new PsiInvalidElementAccessException(method));
    }
    myMethod = method;
  }

  @Nonnull
  @Override
  public String getName() {
    return myMethod.getName();
  }

  @Override
  public boolean isRaw() {
    return myIsRaw;
  }

  @Override
  public boolean isConstructor() {
    return myMethod.isConstructor();
  }

  public boolean equals(Object o) {
    if (o instanceof MethodSignatureBackedByPsiMethod){ // optimization
      if (((MethodSignatureBackedByPsiMethod)o).myMethod == myMethod) return true;
    }

    return super.equals(o);
  }

  @Nonnull
  public PsiMethod getMethod() {
    return myMethod;
  }

  public static MethodSignatureBackedByPsiMethod create(@Nonnull PsiMethod method, @Nonnull PsiSubstitutor substitutor) {
    return create(method, substitutor, PsiUtil.isRawSubstitutor(method, substitutor));
  }

  public static MethodSignatureBackedByPsiMethod create(@Nonnull PsiMethod method, @Nonnull PsiSubstitutor substitutor, boolean isRaw) {
    PsiTypeParameter[] methodTypeParameters = method.getTypeParameters();
    if (isRaw) {
      substitutor = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createRawSubstitutor(substitutor, methodTypeParameters);
      methodTypeParameters = PsiTypeParameter.EMPTY_ARRAY;
    }
    
    assert substitutor.isValid();

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiType[] parameterTypes = new PsiType[parameters.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      PsiType type = parameters[i].getType();
      assert type.isValid() : type.toString();
      parameterTypes[i] = isRaw ? TypeConversionUtil.erasure(substitutor.substitute(type)) : type;
    }

    return new MethodSignatureBackedByPsiMethod(method, substitutor, isRaw, parameterTypes, methodTypeParameters);
  }
}
