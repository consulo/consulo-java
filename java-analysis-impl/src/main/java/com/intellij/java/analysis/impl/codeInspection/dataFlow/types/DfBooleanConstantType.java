// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.types.DfConstantType;


public final class DfBooleanConstantType extends DfConstantType<Boolean> implements DfBooleanType {
  DfBooleanConstantType(boolean value) {
    super(value);
  }

  @Override
  public DfType join(DfType other) {
    if (other.equals(this)) return this;
    if (other instanceof DfBooleanType) return DfTypes.BOOLEAN;
    return DfType.TOP;
  }

  @Override
  public DfType tryJoinExactly(DfType other) {
    return join(other);
  }

  @Override
  public DfType tryNegate() {
    return getValue() ? DfTypes.FALSE : DfTypes.TRUE;
  }
}
