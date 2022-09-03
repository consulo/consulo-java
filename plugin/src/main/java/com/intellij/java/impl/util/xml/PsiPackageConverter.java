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
package com.intellij.java.impl.util.xml;

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class PsiPackageConverter extends Converter<PsiJavaPackage> implements CustomReferenceConverter<PsiJavaPackage> {
  public PsiJavaPackage fromString(@Nullable @NonNls String s, final ConvertContext context) {
    if (s == null) return null;
    return JavaPsiFacade.getInstance(context.getPsiManager().getProject()).findPackage(s);
  }

  public String toString(@Nullable PsiJavaPackage psiPackage, final ConvertContext context) {
    return psiPackage == null ? null : psiPackage.getQualifiedName();
  }

  @Nonnull
  public PsiReference[] createReferences(GenericDomValue<PsiJavaPackage> genericDomValue, PsiElement element, ConvertContext context) {
    final String s = genericDomValue.getStringValue();
    if (s == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    return new PackageReferenceSet(s, element, ElementManipulators.getOffsetInElement(element)).getPsiReferences();
  }
}
