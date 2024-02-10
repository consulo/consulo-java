// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nonnull;

import java.util.Set;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.BOTTOM;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.TOP;

public class DfNullConstantType extends DfConstantType<Object> implements DfReferenceType {
  DfNullConstantType() {
    super(null);
  }

  @Nonnull
  @Override
  public DfaNullability getNullability() {
    return DfaNullability.NULL;
  }

  @Nonnull
  @Override
  public TypeConstraint getConstraint() {
    return TypeConstraints.TOP;
  }

  @Override
  public DfType tryNegate() {
    return DfTypes.NOT_NULL_OBJECT;
  }

  @Nonnull
  @Override
  public PsiType getPsiType() {
    return PsiType.NULL;
  }

  @Nonnull
  @Override
  public DfReferenceType dropNullability() {
    return DfTypes.OBJECT_OR_NULL;
  }

  @Nonnull
  @Override
  public DfType join(@Nonnull DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (!(other instanceof DfReferenceType)) return TOP;
    DfReferenceType type = (DfReferenceType)other;
    return new DfGenericObjectType(Set.of(), type.getConstraint(), DfaNullability.NULL.unite(type.getNullability()),
                                   type.getMutability(), null, BOTTOM, false);
  }
}
