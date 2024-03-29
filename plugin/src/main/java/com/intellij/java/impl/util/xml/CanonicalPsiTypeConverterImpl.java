/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import consulo.document.util.TextRange;
import consulo.language.psi.ElementManipulator;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.xml.util.xml.ConvertContext;
import consulo.xml.util.xml.CustomReferenceConverter;
import consulo.xml.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
public class CanonicalPsiTypeConverterImpl extends CanonicalPsiTypeConverter implements CustomReferenceConverter<PsiType> {

  @NonNls
  static final String[] PRIMITIVES = new String[]{"boolean", "byte",
      "char", "double", "float", "int", "long", "short"};
  @NonNls
  private static final String ARRAY_PREFIX = "[L";
  private static final JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  public PsiType fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    try {
      return JavaPsiFacade.getInstance(context.getFile().getProject()).getElementFactory().createTypeFromText(s.replace('$', '.'), null);
    } catch (IncorrectOperationException e) {
      return null;
    }
  }

  public String toString(final PsiType t, final ConvertContext context) {
    return t == null ? null : t.getCanonicalText();
  }

  @Nonnull
  public PsiReference[] createReferences(final GenericDomValue<PsiType> genericDomValue, final PsiElement element, ConvertContext context) {
    final String str = genericDomValue.getStringValue();
    if (str == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null;
    String trimmed = str.trim();
    int offset = manipulator.getRangeInElement(element).getStartOffset() + str.indexOf(trimmed);
    if (trimmed.startsWith(ARRAY_PREFIX)) {
      offset += ARRAY_PREFIX.length();
      if (trimmed.endsWith(";")) {
        trimmed = trimmed.substring(ARRAY_PREFIX.length(), trimmed.length() - 1);
      } else {
        trimmed = trimmed.substring(ARRAY_PREFIX.length());
      }
    }
    return new JavaClassReferenceSet(trimmed, element, offset, false, CLASS_REFERENCE_PROVIDER) {
      protected JavaClassReference createReference(final int referenceIndex, final String subreferenceText, final TextRange textRange,
                                                   final boolean staticImport) {
        return new JavaClassReference(this, textRange, referenceIndex, subreferenceText, staticImport) {
          public boolean isSoft() {
            return true;
          }

          @Nonnull
          public JavaResolveResult advancedResolve(final boolean incompleteCode) {
            PsiType type = genericDomValue.getValue();
            if (type != null) {
              type = type.getDeepComponentType();
            }
            if (type instanceof PsiPrimitiveType) {
              return new CandidateInfo(element, PsiSubstitutor.EMPTY, false, false, element);
            }

            return super.advancedResolve(incompleteCode);
          }

          public void processVariants(final PsiScopeProcessor processor) {
            if (processor instanceof JavaCompletionProcessor) {
              ((JavaCompletionProcessor) processor).setCompletionElements(getVariants());
            } else {
              super.processVariants(processor);
            }
          }

          @Nonnull
          public Object[] getVariants() {
            final Object[] variants = super.getVariants();
            if (myIndex == 0) {
              return ArrayUtil.mergeArrays(variants, PRIMITIVES);
            }
            return variants;
          }
        };
      }
    }.getAllReferences();
  }
}
