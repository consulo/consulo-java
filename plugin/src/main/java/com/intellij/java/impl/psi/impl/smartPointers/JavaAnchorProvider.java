/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.smartPointers;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.SmartPointerAnchorProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Dennis.Ushakov
 */
@ExtensionImpl
public class JavaAnchorProvider implements SmartPointerAnchorProvider {
  @Override
  public PsiElement getAnchor(@Nonnull PsiElement element) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE) || !element.isPhysical()) {
      return null;
    }

    if (element instanceof PsiAnonymousClass) {
      return ((PsiAnonymousClass) element).getBaseClassReference().getReferenceNameElement();
    }
    if (element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiVariable) {
      return ((PsiNameIdentifierOwner) element).getNameIdentifier();
    }
    if (element instanceof PsiImportList) {
      return element.getContainingFile();
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement restoreElement(@Nonnull PsiElement anchor) {
    if (anchor instanceof PsiIdentifier) {
      PsiElement parent = anchor.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) { // anonymous class, type
        parent = parent.getParent();
      }

      return parent;
    }
    if (anchor instanceof PsiJavaFile) {
      return ((PsiJavaFile) anchor).getImportList();
    }
    return null;
  }
}
