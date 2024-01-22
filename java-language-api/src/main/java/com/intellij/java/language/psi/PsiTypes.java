// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import jakarta.annotation.Nonnull;

import static com.intellij.java.language.psi.PsiType.*;

@SuppressWarnings("deprecation")
public final class PsiTypes {
  /**
   * Returns instance corresponding to {@code byte} type.
   */
  public static @Nonnull
  PsiPrimitiveType byteType() {
    return BYTE;
  }

  /**
   * Returns instance corresponding to {@code char} type.
   */
  public static @Nonnull
  PsiPrimitiveType charType() {
    return CHAR;
  }

  /**
   * Returns instance corresponding to {@code double} type.
   */
  public static @Nonnull
  PsiPrimitiveType doubleType() {
    return DOUBLE;
  }

  /**
   * Returns instance corresponding to {@code float} type.
   */
  public static @Nonnull
  PsiPrimitiveType floatType() {
    return FLOAT;
  }

  /**
   * Returns instance corresponding to {@code int} type.
   */
  public static @Nonnull
  PsiPrimitiveType intType() {
    return INT;
  }

  /**
   * Returns instance corresponding to {@code long} type.
   */
  public static @Nonnull
  PsiPrimitiveType longType() {
    return LONG;
  }

  /**
   * Returns instance corresponding to {@code short} type.
   */
  public static @Nonnull
  PsiPrimitiveType shortType() {
    return SHORT;
  }

  /**
   * Returns instance corresponding to {@code boolean} type.
   */
  public static @Nonnull
  PsiPrimitiveType booleanType() {
    return BOOLEAN;
  }

  /**
   * Returns instance corresponding to {@code void} type.
   */
  public static @Nonnull
  PsiPrimitiveType voidType() {
    return VOID;
  }

  /**
   * Returns instance describing the type of {@code null} value.
   */
  public static @Nonnull
  PsiType nullType() {
    return NULL;
  }

  private PsiTypes() {
  }
}
