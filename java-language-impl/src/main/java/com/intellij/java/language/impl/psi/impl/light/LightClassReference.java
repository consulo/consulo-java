/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class LightClassReference extends LightClassReferenceBase implements PsiJavaCodeReferenceElement {

  private final String myClassName;
  private final PsiElement myContext;
  @Nonnull
  private final GlobalSearchScope myResolveScope;
  private final PsiClass myRefClass;
  private final PsiSubstitutor mySubstitutor;

  private LightClassReference(@Nonnull PsiManager manager,
                              @Nonnull @NonNls String text,
                              @Nullable @NonNls String className,
                              @Nullable PsiSubstitutor substitutor,
                              @Nonnull GlobalSearchScope resolveScope,
                              @Nullable PsiElement context,
                              @Nullable PsiClass refClass) {
    super(manager, text);
    myClassName = className;
    myResolveScope = resolveScope;

    myContext = context;
    myRefClass = refClass;
    mySubstitutor = substitutor;
  }

  public LightClassReference(@Nonnull PsiManager manager, @Nonnull @NonNls String text, @Nonnull @NonNls String className, @Nonnull GlobalSearchScope resolveScope) {
    this(manager, text, className, null, resolveScope, null, null);
  }

  public LightClassReference(@Nonnull PsiManager manager,
                             @Nonnull @NonNls String text,
                             @Nonnull @NonNls String className,
                             PsiSubstitutor substitutor,
                             @Nonnull PsiElement context) {
    this(manager, text, className, substitutor, context.getResolveScope(), context, null);
  }

  public LightClassReference(@Nonnull PsiManager manager, @Nonnull @NonNls String text, @Nonnull PsiClass refClass) {
    this(manager, text, refClass, null);
  }

  public LightClassReference(@Nonnull PsiManager manager, @Nonnull @NonNls String text, @Nonnull PsiClass refClass, PsiSubstitutor substitutor) {
    this(manager, text, null, substitutor, refClass.getResolveScope(), null, refClass);
  }

  @Override
  public PsiElement resolve() {
    if (myClassName != null) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      if (myContext != null) {
        return facade.getResolveHelper().resolveReferencedClass(myClassName, myContext);
      } else {
        return facade.findClass(myClassName, myResolveScope);
      }
    } else {
      return myRefClass;
    }
  }

  @Override
  @Nonnull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final PsiElement resolved = resolve();
    if (resolved == null) {
      return JavaResolveResult.EMPTY;
    }
    PsiSubstitutor substitutor = mySubstitutor;
    if (substitutor == null) {
      if (resolved instanceof PsiClass) {
        substitutor = JavaPsiFacade.getElementFactory(myManager.getProject()).createRawSubstitutor((PsiClass) resolved);
      } else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    return new CandidateInfo(resolved, substitutor);
  }

  @Override
  public String getQualifiedName() {
    if (myClassName != null) {
      if (myContext != null) {
        PsiClass psiClass = (PsiClass) resolve();
        if (psiClass != null) {
          return psiClass.getQualifiedName();
        }
      }
      return myClassName;
    }
    return myRefClass.getQualifiedName();
  }

  @Override
  public String getReferenceName() {
    if (myClassName != null) {
      return PsiNameHelper.getShortClassName(myClassName);
    } else {
      if (myRefClass instanceof PsiAnonymousClass) {
        return ((PsiAnonymousClass) myRefClass).getBaseClassReference().getReferenceName();
      } else {
        return myRefClass.getName();
      }
    }
  }

  @Override
  public PsiElement copy() {
    if (myClassName != null) {
      if (myContext != null) {
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myContext);
      } else {
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myResolveScope, null, null);
      }
    } else {
      return new LightClassReference(myManager, myText, myRefClass, mySubstitutor);
    }
  }

  @Override
  public boolean isValid() {
    return myRefClass == null || myRefClass.isValid();
  }

  @Nonnull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myResolveScope;
  }
}
