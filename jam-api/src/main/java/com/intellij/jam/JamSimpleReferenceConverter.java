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

import com.intellij.jam.model.common.CommonModelElement;
import com.intellij.java.language.psi.PsiLiteral;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.util.collection.ContainerUtil;
import consulo.xml.util.xml.ElementPresentationManager;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

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
  protected PsiElement getPsiElementFor(@jakarta.annotation.Nonnull T target) {
    if (target instanceof PsiElement) {
      return (PsiElement)target;
    } else if (target instanceof CommonModelElement) {
      return ((CommonModelElement)target).getIdentifyingPsiElement();
    }
    return null;
  }

  @jakarta.annotation.Nonnull
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
    return ContainerUtil.map2Array(getVariants(context), LookupElement.class, new Function<T, LookupElement>() {
      @Nonnull
      public LookupElement apply(T t) {
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
