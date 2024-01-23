// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiRecordComponent;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import one.util.streamex.StreamEx;

class LightRecordComponentModifierList extends LightModifierList {
  private final PsiModifierListOwner myParent;
  @Nonnull
  private final PsiAnnotation.TargetType[] myTargets;
  private final PsiRecordComponent myRecordComponent;

  LightRecordComponentModifierList(@Nonnull PsiModifierListOwner parent, @Nonnull PsiModifierListOwner prototype,
                                   @Nonnull PsiRecordComponent component) {
    super(prototype);
    myParent = parent;
    myRecordComponent = component;
    myTargets = AnnotationTargetUtil.getTargetsForLocation(this);
  }

  LightRecordComponentModifierList(@Nonnull PsiModifierListOwner parent, @Nonnull PsiManager manager,
                                   @Nonnull PsiRecordComponent component) {
    super(manager);
    myParent = parent;
    myRecordComponent = component;
    myTargets = AnnotationTargetUtil.getTargetsForLocation(this);
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void setModifierProperty(@Nonnull String name, boolean value) throws IncorrectOperationException {
    if (hasModifierProperty(name) == value) return;
    super.setModifierProperty(name, value);
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getAnnotations() {
    PsiAnnotation[] annotations = myRecordComponent.getAnnotations();
    if (annotations.length == 0) return annotations;
    return StreamEx.of(annotations).filter(
                     anno -> AnnotationTargetUtil.findAnnotationTarget(anno, myTargets) != null)
                   .toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public PsiAnnotation findAnnotation(@Nonnull String qualifiedName) {
    PsiModifierList list = myRecordComponent.getModifierList();
    if (list == null) return null;
    PsiAnnotation annotation = list.findAnnotation(qualifiedName);
    if (annotation != null && AnnotationTargetUtil.findAnnotationTarget(annotation, myTargets) != null) {
      return annotation;
    }
    return null;
  }

  @Override
  public boolean hasAnnotation(@Nonnull String qualifiedName) {
    //noinspection SSBasedInspection
    return findAnnotation(qualifiedName) != null;
  }
}
