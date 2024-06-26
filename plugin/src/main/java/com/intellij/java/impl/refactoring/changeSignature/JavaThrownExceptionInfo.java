/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.changeSignature;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.*;
import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class JavaThrownExceptionInfo implements ThrownExceptionInfo {
  private final int oldIndex;
  private CanonicalTypes.Type myType;

  public JavaThrownExceptionInfo() {
    oldIndex = -1;
  }

  public JavaThrownExceptionInfo(int oldIndex) {
    this.oldIndex = oldIndex;
  }

  public JavaThrownExceptionInfo(int oldIndex, PsiClassType type) {
    this.oldIndex = oldIndex;
    setType(type);
  }

  //create identity mapping
  public static ThrownExceptionInfo[] extractExceptions(PsiMethod method) {
    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] result = new ThrownExceptionInfo[types.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new JavaThrownExceptionInfo(i, types[i]);
    }
    return result;
  }

  public void setType(PsiClassType type) {
    myType = CanonicalTypes.createTypeWrapper(type);
  }

  @Nullable
  public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    if (myType != null) {
      return myType.getType(context, manager);
    }
    else {
      return null;
    }
  }

  public void updateFromMethod(PsiMethod method) {
    if (myType != null) return;
    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    if (oldIndex >= 0) {
      setType(types[oldIndex]);
    }
  }

  public int getOldIndex() {
    return oldIndex;
  }
}
