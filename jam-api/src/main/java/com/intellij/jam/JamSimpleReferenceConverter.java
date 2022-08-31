/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.jam;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.jam.model.common.CommonModelElement;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiLiteral;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ElementPresentationManager;

/**
 * @author peter
 */
public abstract class JamSimpleReferenceConverter<T> extends JamConverter<T>{
  @Nonnull
  @Override
  public PsiReference[] createReferences(JamStringAttributeElement<T> context) {
    final PsiLiteral literal = context.getPsiLiteral();
    if (literal == null) return PsiReference.EMPTY_ARRAY;

    return new PsiReference[]{createReference(context)};
  }

  protected JamSimpleReference<T> createReference(JamStringAttributeElement<T> context) {
    return new JamSimpleReference<T>(context);
  }

  @Nullable
  protected PsiElement getPsiElementFor(@Nonnull T target) {
    if (target instanceof PsiElement) {
      return (PsiElement)target;
    } else if (target instanceof CommonModelElement) {
      return ((CommonModelElement)target).getIdentifyingPsiElement();
    }
    return null;
  }

  @Nonnull
  protected LookupElement createLookupElementFor(@Nonnull T target) {
    String name = ElementPresentationManager.getElementName(target);
    if (name != null) {
      return LookupElementBuilder.create(name);
    }
    final PsiElement psiElement = getPsiElementFor(target);
    if (psiElement instanceof PsiNamedElement) {
      return LookupElementBuilder.create((PsiNamedElement)psiElement).withIcon(ElementPresentationManager.getIcon(target));
    }
    throw new UnsupportedOperationException("Cannot convert "+target+", PSI:" + psiElement);
  }

  public LookupElement[] getLookupVariants(JamStringAttributeElement<T> context) {
    return ContainerUtil.map2Array(getVariants(context), LookupElement.class, new NotNullFunction<T, LookupElement>() {
      @Nonnull
      public LookupElement fun(T t) {
        return createLookupElementFor(t);
      }
    });
  }

  public Collection<T> getVariants(JamStringAttributeElement<T> context) {
    return Collections.emptyList();
  }

  public PsiElement bindReference(JamStringAttributeElement<T> context, PsiElement element) {
    throw new UnsupportedOperationException();
  }
}
