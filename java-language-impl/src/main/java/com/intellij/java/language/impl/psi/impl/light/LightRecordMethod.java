// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.DumbService;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;

public final class LightRecordMethod extends LightMethod implements LightRecordMember {
  @Nonnull
  private final
  PsiRecordComponent myRecordComponent;

  public LightRecordMethod(@Nonnull PsiManager manager,
                           @Nonnull PsiMethod method,
                           @Nonnull PsiClass containingClass,
                           @Nonnull PsiRecordComponent component) {
    super(manager, method, containingClass);
    myRecordComponent = component;
  }

  @Override
  @Nonnull
  public PsiRecordComponent getRecordComponent() {
    return myRecordComponent;
  }

  @Override
  public int getTextOffset() {
    return myRecordComponent.getTextOffset();
  }

  @Nonnull
  @Override
  public PsiElement getNavigationElement() {
    return myRecordComponent.getNavigationElement();
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass.getContainingFile();
  }

  @Override
  public PsiType getReturnType() {
    if (DumbService.isDumb(myRecordComponent.getProject())) {
      return myRecordComponent.getType();
    }
    return LanguageCachedValueUtil.getCachedValue(this, () -> {
      PsiType type = myRecordComponent.getType()
          .annotate(() -> Arrays.stream(myRecordComponent.getAnnotations())
              .filter(LightRecordMethod::hasTargetApplicableForMethod)
              .toArray(PsiAnnotation[]::new)
          );
      return CachedValueProvider.Result.create(type, this);
    });
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getAnnotations() {
    PsiType returnType = getReturnType();
    if (returnType == null) {
      return PsiAnnotation.EMPTY_ARRAY;
    }
    return returnType.getAnnotations();
  }

  @Override
  public boolean hasAnnotation(@Nonnull String fqn) {
    PsiType returnType = getReturnType();
    return returnType != null && returnType.hasAnnotation(fqn);
  }

  @Override
  @Nullable
  public PsiAnnotation getAnnotation(@Nonnull String fqn) {
    PsiType returnType = getReturnType();
    if (returnType == null) {
      return null;
    }
    return returnType.findAnnotation(fqn);
  }


  @Override
  public PsiElement getContext() {
    return getContainingClass();
  }

  private static boolean hasTargetApplicableForMethod(PsiAnnotation annotation) {
    return AnnotationTargetUtil.findAnnotationTarget(annotation, PsiAnnotation.TargetType.TYPE_USE, PsiAnnotation.TargetType.METHOD) != null;
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    return myRecordComponent.setName(name);
  }

  @Override
  public PsiElement copy() {
    return myMethod.copy();
  }
}
