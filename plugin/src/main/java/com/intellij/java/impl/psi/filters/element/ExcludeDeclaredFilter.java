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
package com.intellij.java.impl.psi.filters.element;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.filter.position.PositionElementFilter;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.util.lang.ref.SoftReference;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 26.02.2003
 * Time: 12:31:24
 * To change this template use Options | File Templates.
 */
public class ExcludeDeclaredFilter extends PositionElementFilter{
  public ExcludeDeclaredFilter(ElementFilter filter){
    setFilter(filter);
  }

  SoftReference<PsiElement> myCachedVar = new SoftReference<PsiElement>(null);
  SoftReference<PsiElement> myCurrentContext = new SoftReference<PsiElement>(null);

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    PsiElement cachedVar = context;

    if(myCurrentContext.get() != context){
      myCurrentContext = new SoftReference<PsiElement>(context);
      while(cachedVar != null && !(getFilter().isAcceptable(cachedVar, cachedVar.getContext())))
        cachedVar = cachedVar.getContext();
      myCachedVar = new SoftReference<PsiElement>(cachedVar);
    }

    if (element instanceof PsiMethod && myCachedVar.get() instanceof PsiMethod)  {
      PsiMethod currentMethod = (PsiMethod) element;
      PsiMethod candidate = (PsiMethod) myCachedVar.get();
      return !candidate.getManager().areElementsEquivalent(candidate, currentMethod) && !isOverridingMethod(currentMethod, candidate);
    }
    else if(element instanceof PsiClassType){
      PsiClass psiClass = ((PsiClassType)element).resolve();
      return isAcceptable(psiClass, context);
    }
    else if(context != null){
      if(element instanceof PsiElement)
        return !context.getManager().areElementsEquivalent(myCachedVar.get(), (PsiElement)element);
      return true;
    }
    return true;
  }

  //TODO check exotic conditions like overriding method in package local class from class in other package
  private static boolean isOverridingMethod(PsiMethod method, PsiMethod candidate) {
    if (method.getManager().areElementsEquivalent(method, candidate)) return false;
    if (!MethodSignatureUtil.areSignaturesEqual(method,candidate)) return false;
    PsiClass candidateContainingClass = candidate.getContainingClass();
    return candidateContainingClass.isInheritor(method.getContainingClass(), true);
  }
}
