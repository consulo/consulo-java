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
package com.intellij.java.impl.psi.filters;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.ElementFilter;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PsiMethodCallFilter implements ElementFilter {
  @NonNls
  private final String myClassName;
  @NonNls
  private final Set<String> myMethodNames;


  public PsiMethodCallFilter(@NonNls final String className, @NonNls final String... methodNames) {
    myClassName = className;
    myMethodNames = new HashSet<String>(Arrays.asList(methodNames));
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
      final PsiMethod psiMethod = callExpression.resolveMethod();
      if (psiMethod != null) {
        if (!myMethodNames.contains(psiMethod.getName())) {
          return false;
        }
        final PsiClass psiClass = psiMethod.getContainingClass();
        final PsiClass expectedClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(myClassName, psiClass.getResolveScope());
        return InheritanceUtil.isInheritorOrSelf(psiClass, expectedClass, true);
      }
    }
    return false;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return PsiMethodCallExpression.class.isAssignableFrom(hintClass);
  }

  @NonNls
  public String toString() {
    return "methodcall(" + myClassName + "." + myMethodNames + ")";
  }
}
