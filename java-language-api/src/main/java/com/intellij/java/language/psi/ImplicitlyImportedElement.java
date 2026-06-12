// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

/**
 * Represents an element implicitly imported in a java file
 * (It doesn't include implicitly imported packages (see {@link PsiJavaFile#getImplicitlyImportedPackages()}))
 */
public interface ImplicitlyImportedElement {
  ImplicitlyImportedElement[] EMPTY_ARRAY = new ImplicitlyImportedElement[0];

  /**
   * @return a new or cached instance of {@code PsiImportStatementBase} representing an implicitly imported element.
   */
  PsiImportStatementBase createImportStatement();
}
