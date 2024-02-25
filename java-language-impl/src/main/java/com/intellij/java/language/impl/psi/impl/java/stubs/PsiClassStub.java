// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.psi.PsiClass;
import jakarta.annotation.Nullable;

public interface PsiClassStub<T extends PsiClass> extends PsiMemberStub<T> {
  @Nullable
  String getQualifiedName();

  @Nullable
  String getBaseClassReferenceText();

  boolean isInterface();

  boolean isEnum();

  default boolean isRecord() {
    return false;
  }

  default boolean isImplicit() {
    return false;
  }

  boolean isEnumConstantInitializer();

  boolean isAnonymous();

  boolean isAnonymousInQualifiedNew();

  boolean isAnnotationType();

  @Nullable
  String getSourceFileName();
}
