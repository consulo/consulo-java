/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.java.language.impl.psi.scope.processor;

import consulo.util.dataholder.Key;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.resolve.ResolveState;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.NameHint;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class MethodResolveProcessor implements PsiScopeProcessor, ElementClassHint, NameHint {

  private final String myNameHint;
  private final List<PsiMethod> myMethods = new ArrayList<PsiMethod>();

  public MethodResolveProcessor() {
    myNameHint = null;
  }

  public MethodResolveProcessor(final String name) {
    myNameHint = name;
  }

  public PsiMethod[] getMethods() {
    return myMethods.toArray(new PsiMethod[myMethods.size()]);
  }

  public boolean execute(@Nonnull PsiElement element, ResolveState state) {
    if (element instanceof PsiMethod) {
      ContainerUtil.addIfNotNull(myMethods, (PsiMethod)element);
    }
    return true;
  }

  public <T> T getHint(@Nonnull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }
    if (hintKey == NameHint.KEY && myNameHint != null) {
      return (T)this;
    }
    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }

  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.METHOD;
  }

  public static PsiMethod[] findMethod(PsiClass psiClass, String methodName) {
    MethodResolveProcessor processor = new MethodResolveProcessor(methodName);
    psiClass.processDeclarations(processor, ResolveState.initial(), null, psiClass);
    return processor.getMethods();
  }

  public static PsiMethod[] getAllMethods(PsiClass psiClass) {
    MethodResolveProcessor processor = new MethodResolveProcessor();
    psiClass.processDeclarations(processor, ResolveState.initial(), null, psiClass);
    return processor.getMethods();
  }


  @Nullable
  @Override
  public String getName(ResolveState state) {
    return myNameHint;
  }
}
