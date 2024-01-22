// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmParameter;
import consulo.language.psi.PsiElement;
import consulo.navigation.NavigationItem;
import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents the parameter of a Java method, foreach (enhanced for) statement or catch block.
 */
public interface PsiParameter extends PsiVariable, JvmParameter, PsiJvmModifiersOwner, NavigationItem {
  /**
   * The empty array of PSI parameters which can be reused to avoid unnecessary allocations.
   */
  PsiParameter[] EMPTY_ARRAY = new PsiParameter[0];

  ArrayFactory<PsiParameter> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiParameter[count];

  /**
   * Returns the element (method, lambda expression, foreach statement or catch block) in which the
   * parameter is declared.
   *
   * @return the declaration scope for the parameter.
   */
  @Nonnull
  PsiElement getDeclarationScope();

  /**
   * Checks if the parameter accepts a variable number of arguments.
   *
   * @return true if the parameter is a vararg, false otherwise
   */
  boolean isVarArgs();

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  PsiTypeElement getTypeElement();

  // This explicit declaration is required to force javac to generate a bridge method 'JvmType getType()'; without it calling
  // JvmParameter#getType() method on instances which weren't recompiled against the new API will cause AbstractMethodError.
  @Override
  @Nonnull
  PsiType getType();

  // binary compatibility
  @Override
  @Nonnull
  default PsiAnnotation [] getAnnotations() {
    return PsiJvmModifiersOwner.super.getAnnotations();
  }

  @Override
  @Nonnull
  String getName();
}
