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
package com.intellij.java.impl.psi.impl.source.javadoc;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiReference;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.javadoc.JavadocTagInfo;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;

/**
 * @author mike
 */
class ReturnDocTagInfo implements JavadocTagInfo {
  @Override
  public String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  @Override
  public String getName() {
    return "return";
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)element;
    final PsiType type = method.getReturnType();
    if (type == null) return false;
    return !PsiType.VOID.equals(type);
  }

  @Override
  public boolean isInline() {
    return false;
  }
}
