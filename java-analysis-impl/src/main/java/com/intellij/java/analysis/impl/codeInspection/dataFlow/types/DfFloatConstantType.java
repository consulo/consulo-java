// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.language.psi.PsiPrimitiveType;
import jakarta.annotation.Nonnull;

import java.util.Collections;

class DfFloatConstantType extends DfConstantType<Float> implements DfFloatType {
  DfFloatConstantType(float value) {
    super(value);
  }

  @Nonnull
  @Override
  public DfType join(@Nonnull DfType other) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfFloatType) return DfTypes.FLOAT;
    return DfTypes.TOP;
  }

  @Nonnull
  @Override
  public PsiPrimitiveType getPsiType() {
    return DfFloatType.super.getPsiType();
  }

  @Nonnull
  @Override
  public DfType tryNegate() {
    return new DfFloatNotValueType(Collections.singleton(getValue()));
  }
}
