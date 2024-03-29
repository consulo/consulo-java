// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import one.util.streamex.StreamEx;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a type that maintains a set of constants that excluded from this type
 */
public abstract class DfAntiConstantType<T> implements DfType {
  final @Nonnull
  Set<T> myNotValues;
  
  DfAntiConstantType(@Nonnull Set<T> notValues) {
    myNotValues = notValues;
  }

  /**
   * @return set of excluded constants
   */
  @Nonnull
  public Set<T> getNotValues() {
    return Collections.unmodifiableSet(myNotValues);
  }

  @Override
  public int hashCode() {
    return myNotValues.hashCode()+1234;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof DfAntiConstantType && Objects.equals(((DfAntiConstantType<?>)obj).myNotValues, myNotValues);
  }

  @Override
  public String toString() {
    return "!= " + StreamEx.of(myNotValues).map(DfConstantType::renderValue).joining(", ");
  }
}
