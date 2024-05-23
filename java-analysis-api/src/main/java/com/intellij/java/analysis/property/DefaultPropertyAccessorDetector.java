// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.property;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PropertyUtilBase;
import com.intellij.java.language.util.PropertyKind;
import consulo.annotation.access.RequiredReadAction;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

final class DefaultPropertyAccessorDetector {
  @Nullable
  @RequiredReadAction
  static PropertyAccessorDetector.PropertyAccessorInfo getDefaultAccessorInfo(@Nonnull PsiMethod method) {
    if (PropertyUtilBase.isSimplePropertyGetter(method)) {
      return new PropertyAccessorDetector.PropertyAccessorInfo(PropertyUtilBase.getPropertyNameByGetter(method),
                                                               method.getReturnType(),
                                                               PropertyKind.GETTER);
    }
    else if (PropertyUtilBase.isSimplePropertySetter(method)) {
      return new PropertyAccessorDetector.PropertyAccessorInfo(PropertyUtilBase.getPropertyNameBySetter(method),
                                                               method.getParameterList().getParameters()[0].getType(),
                                                               PropertyKind.SETTER);
    }
    return null;
  }
}
