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
package com.intellij.java.impl.psi.filters.classes;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.ref.SoftReference;
import consulo.util.lang.reflect.ReflectionUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 26.03.2003
 * Time: 21:01:47
 * To change this template use Options | File Templates.
 */
public abstract class ClassAssignableFilter implements ElementFilter{
  protected String myClassName = null;
  protected PsiClass myClass = null;
  private SoftReference myCachedClass = new SoftReference(null);

  @Override
  public abstract boolean isAcceptable(Object aClass, PsiElement context);
  public abstract String toString();

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionUtil.isAssignable(PsiClass.class, hintClass);
  }

  protected PsiClass getPsiClass(PsiManager manager, GlobalSearchScope scope){
    if(myClass != null){
      return myClass;
    }

    if(myCachedClass.get() == null && manager != null){
      myCachedClass = new SoftReference(JavaPsiFacade.getInstance(manager.getProject()).findClass(myClassName, scope));
    }
    return (PsiClass) myCachedClass.get();
  }

}
