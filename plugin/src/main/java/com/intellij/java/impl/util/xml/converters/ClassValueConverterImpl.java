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
package com.intellij.java.impl.util.xml.converters;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.java.impl.util.xml.converters.values.ClassValueConverter;

/**
 * User: Sergey.Vasiliev
 */
@Singleton
public class ClassValueConverterImpl extends ClassValueConverter {
  private static final JavaClassReferenceProvider REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  static {
    REFERENCE_PROVIDER.setSoft(true);
    REFERENCE_PROVIDER.setAllowEmpty(true);
  }

  @Nonnull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    return REFERENCE_PROVIDER.getReferencesByElement(element);
  }
}
