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
package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.*;
import consulo.language.psi.search.RequestResultProcessor;

import jakarta.annotation.Nonnull;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * @author peter
 */
public class MethodTextOccurrenceProcessor extends RequestResultProcessor {
  private  final PsiReferenceService myReferenceService;
  private final PsiMethod[] myMethods;
  protected final PsiClass myContainingClass;
  protected final boolean myStrictSignatureSearch;

  public MethodTextOccurrenceProcessor(@Nonnull final PsiClass aClass,
                                       final boolean strictSignatureSearch,
                                       final PsiMethod... methods) {
    super(strictSignatureSearch, Arrays.asList(methods));
    myMethods = methods;
    myContainingClass = aClass;
    myStrictSignatureSearch = strictSignatureSearch;
    myReferenceService = PsiReferenceService.getService();
  }

  @Override
  public final boolean processTextOccurrence(@Nonnull PsiElement element,
                                             int offsetInElement,
                                             @Nonnull final Predicate<? super PsiReference> consumer) {
    for (PsiReference ref : myReferenceService.getReferences(element, new PsiReferenceService.Hints(myMethods[0],
                                                                                                    offsetInElement))) {
      if (ReferenceRange.containsOffsetInElement(ref, offsetInElement) && !processReference(consumer, ref)) {
        return false;
      }
    }
    return true;
  }

  private boolean processReference(Predicate<? super PsiReference> consumer, PsiReference ref) {
    for (PsiMethod method : myMethods) {
      if (!method.isValid()) {
        continue;
      }

      if (ref instanceof ResolvingHint && !((ResolvingHint) ref).canResolveTo(PsiMethod.class)) {
        return true;
      }
      if (ref.isReferenceTo(method)) {
        return consumer.test(ref);
      }

      if (!processInexactReference(ref, ref.resolve(), method, consumer)) {
        return false;
      }
    }

    return true;
  }

  protected boolean processInexactReference(PsiReference ref,
                                            PsiElement refElement,
                                            PsiMethod method,
                                            Predicate<? super PsiReference> consumer) {
    if (refElement instanceof PsiMethod) {
      PsiMethod refMethod = (PsiMethod) refElement;
      PsiClass refMethodClass = refMethod.getContainingClass();
      if (refMethodClass == null) {
        return true;
      }

      if (!refMethod.hasModifierProperty(PsiModifier.STATIC)) {
        PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(myContainingClass, refMethodClass,
            PsiSubstitutor.EMPTY);
        if (substitutor != null) {
          MethodSignature superSignature = method.getSignature(substitutor);
          MethodSignature refSignature = refMethod.getSignature(PsiSubstitutor.EMPTY);

          if (MethodSignatureUtil.isSubsignature(superSignature, refSignature)) {
            if (!consumer.test(ref)) {
              return false;
            }
          }
        }
      }

      if (!myStrictSignatureSearch) {
        PsiManager manager = method.getManager();
        if (manager.areElementsEquivalent(refMethodClass, myContainingClass)) {
          if (!consumer.test(ref)) {
            return false;
          }
        }
      }
    }

    return true;
  }

}
