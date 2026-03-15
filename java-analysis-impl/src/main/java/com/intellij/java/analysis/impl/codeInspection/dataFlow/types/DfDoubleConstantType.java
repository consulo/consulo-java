// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.language.psi.PsiPrimitiveType;

import java.util.Collections;

class DfDoubleConstantType extends DfConstantType<Double> implements DfDoubleType {
  DfDoubleConstantType(double value) {
    super(value);
  }

  @Override
  public DfType join(DfType other) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfDoubleType) return DfTypes.DOUBLE;
    return DfTypes.TOP;
  }

  @Override
  public PsiPrimitiveType getPsiType() {
    return DfDoubleType.super.getPsiType();
  }

  @Override
  public DfType tryNegate() {
    return new DfDoubleNotValueType(Collections.singleton(getValue()));
  }
}
