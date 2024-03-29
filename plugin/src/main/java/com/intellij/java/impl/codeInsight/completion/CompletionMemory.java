/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.document.util.Segment;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nullable;

import java.util.LinkedList;

/**
 * from kotlin
 * <p>
 * intellij-community\java\java-impl\src\com\intellij\codeInsight\completion\CompletionMemory.kt
 */
public class CompletionMemory {
  private static final Key<LinkedList<RangeMarker>> LAST_CHOSEN_METHODS = Key.create("COMPLETED_METHODS");
  private static final Key<SmartPsiElementPointer<PsiMethod>> CHOSEN_METHODS = Key.create("CHOSEN_METHODS");

  @RequiredReadAction
  public static void registerChosenMethod(PsiMethod method, PsiCall call) {
    TextRange nameRange = getAnchorRange(call);
    if (nameRange == null) {
      return;
    }
    Document document = call.getContainingFile().getViewProvider().getDocument();
    if (document == null) {
      return;
    }
    addToMemory(document, createChosenMethodMarker(document, CompletionUtilCore.getOriginalOrSelf(method), nameRange));
  }

  private static void addToMemory(Document document, RangeMarker marker) {
    LinkedList<RangeMarker> completedMethods = new LinkedList<>();

    LinkedList<RangeMarker> rangeMarkers = document.getUserData(LAST_CHOSEN_METHODS);
    if (rangeMarkers != null) {
      completedMethods.addAll(ContainerUtil.filter(rangeMarkers, it -> it.isValid() && !haveSameRange(it, marker)));
    }

    while (completedMethods.size() > 10) {
      completedMethods.remove(0);
    }
    document.putUserData(LAST_CHOSEN_METHODS, completedMethods);

    completedMethods.add(marker);
  }

  private static RangeMarker createChosenMethodMarker(Document document, PsiMethod method, TextRange nameRange) {
    RangeMarker marker = document.createRangeMarker(nameRange.getStartOffset(), nameRange.getEndOffset());
    marker.putUserData(CHOSEN_METHODS, SmartPointerManager.getInstance(method.getProject()).createSmartPsiElementPointer(method));
    return marker;
  }

  @Nullable
  @RequiredReadAction
  private static TextRange getAnchorRange(PsiCall call) {
    if (call instanceof PsiMethodCallExpression) {
      PsiElement referenceNameElement = ((PsiMethodCallExpression) call).getMethodExpression().getReferenceNameElement();
      if (referenceNameElement == null) {
        return null;
      }
      return referenceNameElement.getTextRange();
    } else if (call instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement classOrAnonymousClassReference = ((PsiNewExpression) call).getClassOrAnonymousClassReference();
      if (classOrAnonymousClassReference == null) {
        return null;
      }
      PsiElement referenceNameElement = classOrAnonymousClassReference.getReferenceNameElement();
      if (referenceNameElement == null) {
        return null;
      }
      return referenceNameElement.getTextRange();
    }
    return null;
  }

  @Nullable
  @RequiredReadAction
  public static PsiMethod getChosenMethod(PsiCall call) {
    TextRange range = getAnchorRange(call);
    if (range == null) {
      return null;
    }
    Document document = call.getContainingFile().getViewProvider().getDocument();
    if (document == null) {
      return null;
    }

    LinkedList<RangeMarker> completedMethods = document.getUserData(LAST_CHOSEN_METHODS);
    if (completedMethods == null) {
      return null;
    }

    RangeMarker rangeMarker = ContainerUtil.find(completedMethods, m -> haveSameRange(m, range));
    if (rangeMarker == null) {
      return null;
    }
    SmartPsiElementPointer<PsiMethod> pointer = rangeMarker.getUserData(CHOSEN_METHODS);
    return pointer == null ? null : pointer.getElement();
  }

  private static boolean haveSameRange(Segment s1, Segment s2) {
    return s1.getStartOffset() == s2.getStartOffset() && s1.getEndOffset() == s2.getEndOffset();
  }
}
