/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.modifiers;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import jakarta.annotation.Nonnull;

/**
 * @author Bas Leijdekkers
 */
class ModifierPredicate implements PsiElementPredicate {

  @PsiModifier.ModifierConstant
  private final String myModifier;

  public ModifierPredicate(@Nonnull @PsiModifier.ModifierConstant String modifier) {
    myModifier = modifier;
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass || parent instanceof PsiField || parent instanceof PsiMethod)) {
      return false;
    }
    if (element instanceof PsiDocComment || element instanceof PsiCodeBlock) {
      return false;
    }
    if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      PsiElement brace = aClass.getLBrace();
      if (brace != null && brace.getTextOffset() < element.getTextOffset()) {
        return false;
      }
      if (aClass.getContainingClass() == null &&
          (myModifier.equals(PsiModifier.PRIVATE) || myModifier.equals(PsiModifier.PROTECTED))) {
        return false;
      }
    } else if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || containingClass.isInterface()) {
        return false;
      }
    }
    PsiModifierListOwner owner = (PsiModifierListOwner)parent;
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList == null) {
      return false;
    }
    return !modifierList.hasModifierProperty(myModifier);
  }
}
