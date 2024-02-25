// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.psi.filters.classes;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.reflect.ReflectionUtil;
import jakarta.annotation.Nonnull;

import java.lang.ref.SoftReference;

public class AssignableFromContextFilter implements ElementFilter {

  private SoftReference<PsiElement> myCurrentContext = new SoftReference<>(null);
  private SoftReference<PsiClass> myCachedClass = new SoftReference<>(null);

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return ReflectionUtil.isAssignable(PsiClass.class, hintClass);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    if (myCurrentContext.get() != context) {
      myCurrentContext = new SoftReference<>(context);
      myCachedClass = new SoftReference<>(PsiTreeUtil.getContextOfType(context, false, PsiClass.class));
    }
    PsiClass curClass = myCachedClass.get();
    if (curClass == null) return false;
    PsiClass candidate = ObjectUtil.tryCast(element, PsiClass.class);
    if (candidate == null) return false;
    return checkInheritance(curClass, candidate);
  }

  protected boolean checkInheritance(@Nonnull PsiClass curClass, @Nonnull PsiClass candidate) {
    String qualifiedName = curClass.getQualifiedName();
    return qualifiedName != null && (qualifiedName.equals(candidate.getQualifiedName()) || candidate.isInheritor(curClass, true));
  }

  public String toString() {
    return "assignable-from-context";
  }
}


