// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.scope.processor.ConflictFilterProcessor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated use conflict-filter processor with duplicates resolver {@link ConflictFilterProcessor}
 */
@Deprecated
class AddAllMembersProcessor implements PsiScopeProcessor {
  private final Collection<PsiElement> myAllMembers;
  private final PsiClass myPsiClass;
  private final Map<MethodSignature, PsiMethod> myMethodsBySignature = new HashMap<>();

  AddAllMembersProcessor(@Nonnull Collection<PsiElement> allMembers, @Nonnull PsiClass psiClass) {
    for (PsiElement psiElement : allMembers) {
      if (psiElement instanceof PsiMethod) {
        mapMethodBySignature((PsiMethod) psiElement);
      }
    }
    myAllMembers = allMembers;
    myPsiClass = psiClass;
  }

  @Override
  public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
    PsiMember member = (PsiMember) element;
    if (!isInteresting(element)) {
      return true;
    }
    if (myPsiClass.isInterface() && isObjectMember(element)) {
      return true;
    }
    if (!myAllMembers.contains(member) && isVisible(member, myPsiClass)) {
      if (member instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod) member;
        if (shouldAdd(psiMethod)) {
          mapMethodBySignature(psiMethod);
          myAllMembers.add(PsiImplUtil.handleMirror(psiMethod));
        }
      } else {
        myAllMembers.add(PsiImplUtil.handleMirror(member));
      }
    }
    return true;
  }

  @Nullable
  @Override
  public <T> T getHint(@Nonnull Key<T> hintKey) {
    return null;
  }

  @Override
  public void handleEvent(Event event, @Nullable Object associated) {

  }

  private static boolean isObjectMember(PsiElement element) {
    if (!(element instanceof PsiMethod)) {
      return false;
    }
    final PsiClass containingClass = ((PsiMethod) element).getContainingClass();
    if (containingClass == null) {
      return false;
    } else {
      final String qualifiedName = containingClass.getQualifiedName();
      return qualifiedName != null && qualifiedName.equals(Object.class.getName());
    }
  }

  private void mapMethodBySignature(PsiMethod psiMethod) {
    myMethodsBySignature.put(psiMethod.getSignature(PsiSubstitutor.EMPTY), psiMethod);
  }

  private boolean shouldAdd(PsiMethod psiMethod) {
    MethodSignature signature = psiMethod.getSignature(PsiSubstitutor.EMPTY);
    PsiMethod previousMethod = myMethodsBySignature.get(signature);
    if (previousMethod == null) {
      return true;
    }
    if (isInheritor(psiMethod, previousMethod)) {
      myAllMembers.remove(previousMethod);
      return true;
    }
    return false;
  }

  private static boolean isInteresting(PsiElement element) {
    return element instanceof PsiMethod
        || element instanceof PsiField
        || element instanceof PsiClass
        || element instanceof PsiClassInitializer
        ;
  }

  public static boolean isInheritor(PsiMethod method, PsiMethod baseMethod) {
    return !isStatic(method) && !isStatic(baseMethod) && method.getContainingClass().isInheritor(baseMethod.getContainingClass(), true);
  }

  private static boolean isStatic(PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.STATIC);
  }

  private boolean isVisible(@Nonnull PsiMember element, PsiClass psiClass) {
    return !isInheritedConstructor(element, psiClass) && PsiUtil.isAccessible(element, psiClass, null);
  }

  private static boolean isInheritedConstructor(PsiMember member, PsiClass psiClass) {
    if (!(member instanceof PsiMethod)) {
      return false;
    }
    PsiMethod method = (PsiMethod) member;
    return method.isConstructor() && method.getContainingClass() != psiClass;
  }


}
