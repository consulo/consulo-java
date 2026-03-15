// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.language.psi.PsiPrimitiveType;

class DfBooleanConstantType extends DfConstantType<Boolean> implements DfBooleanType {
  DfBooleanConstantType(boolean value) {
    super(value);
  }

  @Override
  public DfType join(DfType other) {
    if (other.equals(this)) return this;
    if (other instanceof DfBooleanType) return DfTypes.BOOLEAN;
    return DfTypes.TOP;
  }

  @Override
  public PsiPrimitiveType getPsiType() {
    return DfBooleanType.super.getPsiType();
  }

  @Override
  public DfType tryNegate() {
    return getValue() ? DfTypes.FALSE : DfTypes.TRUE;
  }
}
