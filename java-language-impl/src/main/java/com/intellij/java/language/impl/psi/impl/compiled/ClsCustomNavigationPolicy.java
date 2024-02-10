// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface ClsCustomNavigationPolicy {
  ExtensionPointName<ClsCustomNavigationPolicy> EP_NAME = ExtensionPointName.create(ClsCustomNavigationPolicy.class);

  @Nullable
  default PsiElement getNavigationElement(@SuppressWarnings("unused") @Nonnull ClsFileImpl clsFile) {
    return null;
  }

  @Nullable
  default PsiElement getNavigationElement(@SuppressWarnings("unused") @Nonnull ClsClassImpl clsClass) {
    return null;
  }

  @Nullable
  default PsiElement getNavigationElement(@SuppressWarnings("unused") @Nonnull ClsMethodImpl clsMethod) {
    return null;
  }

  @Nullable
  default PsiElement getNavigationElement(@SuppressWarnings("unused") @Nonnull ClsFieldImpl clsField) {
    return null;
  }
}