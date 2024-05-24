// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.property;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.util.PropertyKind;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PropertyAccessorDetector {
  @Nullable
  @RequiredReadAction
  static PropertyAccessorInfo detectFrom(@Nonnull PsiMethod method) {
    Application application = Application.get();
    for (PropertyAccessorDetector detector : application.getExtensionList(PropertyAccessorDetector.class)) {
      PropertyAccessorInfo accessorInfo = detector.detectPropertyAccessor(method);
      if (accessorInfo != null) {
        return accessorInfo;
      }
    }
    return DefaultPropertyAccessorDetector.getDefaultAccessorInfo(method);
  }

  /**
   * Detects property access information if any, or results to null
   */
  @Nullable
  @RequiredReadAction
  PropertyAccessorInfo detectPropertyAccessor(@Nonnull PsiMethod method);

  record PropertyAccessorInfo(@Nonnull String propertyName, @Nonnull PsiType propertyType, @Nonnull PropertyKind kind) {
    public boolean isKindOf(PropertyKind other) {
      return this.kind == other;
    }
  }
}