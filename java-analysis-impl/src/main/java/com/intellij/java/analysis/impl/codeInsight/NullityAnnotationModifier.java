/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight;

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.TypeAnnotationProvider;
import com.intellij.java.language.psi.augment.TypeAnnotationModifier;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * @author peter
 */
@ExtensionImpl
public class NullityAnnotationModifier extends TypeAnnotationModifier {
  @Nullable
  @Override
  public TypeAnnotationProvider modifyAnnotations(@Nonnull PsiType inferenceVariableType, @Nonnull PsiClassType boundType) {
    PsiAnnotation[] annotations = inferenceVariableType.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      String qName = annotation.getQualifiedName();
      if (qName != null && isMatchingAnnotation(boundType, annotation, qName)) {
        return removeAnnotation(annotations, annotation);
      }
    }

    return null;
  }

  @Nonnull
  private static TypeAnnotationProvider removeAnnotation(PsiAnnotation[] annotations, PsiAnnotation annotation) {
    List<PsiAnnotation> list = ContainerUtil.newArrayList(annotations);
    list.remove(annotation);
    if (list.isEmpty()) {
      return TypeAnnotationProvider.EMPTY;
    }

    PsiAnnotation[] array = list.toArray(PsiAnnotation.EMPTY_ARRAY);
    return () -> array;
  }

  private static boolean isMatchingAnnotation(@Nonnull PsiClassType boundType, PsiAnnotation annotation, String qName) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(annotation.getProject());
    return (manager.getNullables().contains(qName) || manager.getNotNulls().contains(qName)) && boundType.findAnnotation(qName) != null;
  }
}
