/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Represents a package access control statement ({@code exports} or {@code opens}) of a Java module declaration.
 *
 * @since 2017.1
 */
public interface PsiPackageAccessibilityStatement extends PsiStatement {
  PsiPackageAccessibilityStatement[] EMPTY_ARRAY = new PsiPackageAccessibilityStatement[0];

  enum Role {
    EXPORTS,
    OPENS
  }

  @Nonnull
  Role getRole();

  @Nullable
  PsiJavaCodeReferenceElement getPackageReference();

  @Nullable
  String getPackageName();

  @Nonnull
  Iterable<PsiJavaModuleReferenceElement> getModuleReferences();

  @Nonnull
  List<String> getModuleNames();
}