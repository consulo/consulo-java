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
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMember;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.StringUtil;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.util.NonCodeSearchDescriptionLocation;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@Nonnull PsiElement element, @Nonnull ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation)) return null;
    NonCodeSearchDescriptionLocation ncdLocation = (NonCodeSearchDescriptionLocation) location;
    if (element instanceof PsiJavaPackage) {
      return ncdLocation.isNonJava() ? ((PsiJavaPackage) element).getQualifiedName() : StringUtil.notNullize(((PsiJavaPackage) element).getName());
    }
    if (element instanceof PsiClass) {
      return ncdLocation.isNonJava() ? ((PsiClass) element).getQualifiedName() : ((PsiClass) element).getName();
    }
    if (element instanceof PsiMember) {
      PsiMember member = (PsiMember) element;
      String name = member.getName();
      if (name == null) return null;
      if (!ncdLocation.isNonJava()) {
        return name;
      }
      PsiClass containingClass = member.getContainingClass();
      if (containingClass == null || containingClass.getQualifiedName() == null) return null;
      return containingClass.getQualifiedName() + "." + name;
    }
    return null;
  }
}
