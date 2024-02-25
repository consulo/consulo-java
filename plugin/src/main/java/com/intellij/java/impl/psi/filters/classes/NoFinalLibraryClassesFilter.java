// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.psi.filters.classes;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.ElementFilter;
import consulo.util.lang.reflect.ReflectionUtil;
import jakarta.annotation.Nullable;

public class NoFinalLibraryClassesFilter implements ElementFilter {
  @Override
  public boolean isAcceptable(Object element, @Nullable PsiElement context) {
    // Do not suggest final/sealed library classes
    return !(element instanceof PsiClass) ||
      !(element instanceof PsiCompiledElement) ||
      !((PsiClass)element).hasModifierProperty(PsiModifier.FINAL) &&
        !((PsiClass)element).hasModifierProperty(PsiModifier.SEALED);
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return ReflectionUtil.isAssignable(PsiClass.class, hintClass);
  }
}
