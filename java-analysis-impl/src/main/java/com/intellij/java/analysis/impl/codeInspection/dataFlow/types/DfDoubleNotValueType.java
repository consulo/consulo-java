// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.language.psi.PsiKeyword;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

class DfDoubleNotValueType extends DfAntiConstantType<Double> implements DfDoubleType {
  DfDoubleNotValueType(Set<Double> values) {
    super(values);
  }

  @Override
  public boolean isSuperType(@Nonnull DfType other) {
    if (other == DfTypes.BOTTOM || other.equals(this)) return true;
    if (other instanceof DfDoubleNotValueType) return ((DfDoubleNotValueType)other).myNotValues.containsAll(myNotValues);
    if (other instanceof DfDoubleConstantType) return !myNotValues.contains(((DfDoubleConstantType)other).getValue());
    return false;
  }

  @Nonnull
  @Override
  public DfType join(@Nonnull DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (other instanceof DfDoubleNotValueType) {
      Set<Double> notValues = new HashSet<>(myNotValues);
      notValues.retainAll(((DfDoubleNotValueType)other).myNotValues);
      return notValues.isEmpty() ? DfTypes.DOUBLE : new DfDoubleNotValueType(notValues);
    }
    return DfTypes.TOP;
  }

  @Nonnull
  @Override
  public DfType meet(@Nonnull DfType other) {
    if (isSuperType(other)) return other;
    if (other.isSuperType(this)) return this;
    if (other instanceof DfDoubleConstantType && myNotValues.contains(((DfDoubleConstantType)other).getValue())) return DfTypes.BOTTOM;
    if (other instanceof DfDoubleNotValueType) {
      Set<Double> notValues = new HashSet<>(myNotValues);
      notValues.addAll(((DfDoubleNotValueType)other).myNotValues);
      return new DfDoubleNotValueType(notValues);
    }
    return DfTypes.BOTTOM;
  }

  @Override
  public String toString() {
    return JavaAnalysisBundle.message("type.presentation.except.values", PsiKeyword.DOUBLE, myNotValues);
  }
}
