// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;

import consulo.language.dataFlow.value.RelationType;
import org.jspecify.annotations.Nullable;

/**
 * Represents linkedOrConsumed field for Stream
 * UNKNOWN-->OPEN
 * \     \
 * ------->CONSUMED
 */
public enum DfStreamStateType implements DfType {
  UNKNOWN, OPEN, CONSUMED;

  @Override
  public boolean isSuperType(DfType other) {
    if (other.equals(this) || other == DfType.BOTTOM) return true;
    if (other instanceof DfStreamStateType && this == UNKNOWN) return true;
    return false;
  }

  @Override
  public DfType join(DfType other) {
    if (other.equals(this) || other == DfType.BOTTOM) return this;
    if (other instanceof DfStreamStateType) return UNKNOWN;
    return DfType.TOP;
  }

  @Override
  public DfType tryJoinExactly(DfType other) {
    if (other.isSuperType(this)) return other;
    if (this.isSuperType(other)) return this;
    return null;
  }

  @Override
  public DfType meet(DfType other) {
    if (other.isSuperType(this)) return this;
    if (this.isSuperType(other)) return other;
    return BOTTOM;
  }

  @Override
  public DfType fromRelation(RelationType relationType) {
    DfType afterRelation = switch (relationType) {
      case EQ -> this;
      case NE -> tryNegate();
      default -> TOP;
    };
    return afterRelation == null ? TOP : afterRelation;
  }

  @Override
  public @Nullable DfType tryNegate() {
    return switch (this) {
      case UNKNOWN -> null;
      case OPEN -> CONSUMED;
      case CONSUMED -> OPEN;
    };
  }

  @Override
  public DfType correctTypeOnFlush(DfType typeBeforeFlush) {
    if (typeBeforeFlush == CONSUMED) return typeBeforeFlush;
    return this;
  }
}
