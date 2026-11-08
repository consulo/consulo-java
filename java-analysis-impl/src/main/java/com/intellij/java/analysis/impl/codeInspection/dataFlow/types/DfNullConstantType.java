// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.types.DfConstantType;

import consulo.language.dataFlow.types.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public class DfNullConstantType extends DfConstantType<Object> implements DfReferenceType {
  DfNullConstantType() {
    super(null);
  }

  @Override
  public DfaNullability getNullability() {
    return DfaNullability.NULL;
  }

  @Override
  public TypeConstraint getConstraint() {
    return TypeConstraints.TOP;
  }

  @Override
  public DfType tryNegate() {
    return DfTypes.NOT_NULL_OBJECT;
  }

  @Override
  public DfReferenceType dropNullability() {
    return DfTypes.OBJECT_OR_NULL;
  }

  @Override
  public DfReferenceType convert(TypeConstraints.TypeConstraintFactory factory) {
    return this;
  }

  @Override
  public DfType join(DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (!(other instanceof DfReferenceType type)) return TOP;
    Set<Object> notValues = type instanceof DfGenericObjectType objectType ? objectType.getRawNotValues() : Set.of();
    return new DfGenericObjectType(notValues, type.getConstraint(), DfaNullability.NULL.unite(type.getNullability()),
                                   type.getMutability(), type.getSpecialField(), type.getSpecialFieldType(), false);
  }

  @Override
  public @Nullable DfType tryJoinExactly(DfType other) {
    if (isMergeable(other)) return this;
    if (other.isMergeable(this)) return other;
    if (other instanceof DfGenericObjectType) return other.tryJoinExactly(this);
    return null;
  }
}
