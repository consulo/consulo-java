// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight;

import com.intellij.java.language.codeInsight.InferredAnnotationProvider;
import com.intellij.java.language.codeInsight.InferredAnnotationsManager;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.component.ServiceImpl;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@Singleton
@ServiceImpl
public class InferredAnnotationsManagerImpl extends InferredAnnotationsManager {
  private static final Key<Boolean> INFERRED_ANNOTATION = Key.create("INFERRED_ANNOTATION");
  private final Project myProject;

  @Inject
  public InferredAnnotationsManagerImpl(Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public PsiAnnotation findInferredAnnotation(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN) {
    for (InferredAnnotationProvider provider : InferredAnnotationProvider.EP_NAME.getExtensionList(myProject)) {
      PsiAnnotation annotation = provider.findInferredAnnotation(listOwner, annotationFQN);
      if (annotation != null) {
        markInferred(annotation);
        return annotation;
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public PsiAnnotation[] findInferredAnnotations(@Nonnull PsiModifierListOwner listOwner) {
    List<PsiAnnotation> result = new ArrayList<>();
    for (InferredAnnotationProvider provider : InferredAnnotationProvider.EP_NAME.getExtensionList(myProject)) {
      List<PsiAnnotation> annotations = provider.findInferredAnnotations(listOwner);
      for (PsiAnnotation annotation : annotations) {
        markInferred(annotation);
        result.add(annotation);
      }
    }
    return result.toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public boolean isInferredAnnotation(@Nonnull PsiAnnotation annotation) {
    return annotation.getUserData(INFERRED_ANNOTATION) != null;
  }

  private static void markInferred(@Nonnull PsiAnnotation annotation) {
    annotation.putUserData(INFERRED_ANNOTATION, Boolean.TRUE);
  }

}
