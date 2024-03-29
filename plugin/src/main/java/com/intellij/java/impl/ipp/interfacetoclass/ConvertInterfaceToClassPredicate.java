/*
 * Copyright 2006-2009 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.interfacetoclass;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.content.scope.SearchScope;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class ConvertInterfaceToClassPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)parent;
    if (!aClass.isInterface() || aClass.isAnnotationType()) {
      return false;
    }
    final PsiElement leftBrace = aClass.getLBrace();
    final int offsetInParent = element.getStartOffsetInParent();
    if (leftBrace == null ||
        offsetInParent >= leftBrace.getStartOffsetInParent()) {
      return false;
    }
    final SearchScope useScope = aClass.getUseScope();
    for (PsiClass inheritor :
      ClassInheritorsSearch.search(aClass, useScope, true)) {
      if (inheritor.isInterface()) {
        return false;
      }
    }
    return true;
  }
}
