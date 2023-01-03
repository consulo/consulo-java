// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.BOTTOM;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.TOP;

public class DfReferenceConstantType extends DfConstantType<Object> implements DfReferenceType {
  private final @Nonnull
  PsiType myPsiType;
  private final @Nonnull
  TypeConstraint myConstraint;
  private final @Nonnull
  Mutability myMutability;
  private final @Nullable
  SpecialField mySpecialField;
  private final @Nonnull
  DfType mySpecialFieldType;
  
  DfReferenceConstantType(@Nonnull Object constant, @Nonnull PsiType psiType, @Nonnull TypeConstraint type) {
    super(constant);
    myPsiType = psiType;
    myConstraint = type;
    myMutability = constant instanceof PsiModifierListOwner ? Mutability.getMutability((PsiModifierListOwner)constant) : Mutability.UNKNOWN;
    mySpecialField = SpecialField.fromQualifierType(psiType);
    mySpecialFieldType = mySpecialField == null ? BOTTOM : mySpecialField.fromConstant(constant);
  }

  @Nonnull
  @Override
  public DfType meet(@Nonnull DfType other) {
    if (other.isSuperType(this)) return this;
    if (other instanceof DfEphemeralReferenceType) return BOTTOM;
    if (other instanceof DfGenericObjectType) {
      DfReferenceType type = ((DfReferenceType)other).dropMutability();
      if (type.isSuperType(this)) return this;
      TypeConstraint constraint = type.getConstraint().meet(myConstraint);
      if (constraint != TypeConstraints.BOTTOM) {
        DfReferenceConstantType subConstant = new DfReferenceConstantType(getValue(), myPsiType, constraint);
        if (type.isSuperType(subConstant)) return subConstant;
      }
    }
    return BOTTOM;
  }

  @Nonnull
  @Override
  public PsiType getPsiType() {
    return myPsiType;
  }

  @Nonnull
  @Override
  public DfaNullability getNullability() {
    return DfaNullability.NOT_NULL;
  }

  @Nonnull
  @Override
  public TypeConstraint getConstraint() {
    return myConstraint;
  }

  @Nonnull
  @Override
  public Mutability getMutability() {
    return myMutability;
  }

  @Nullable
  @Override
  public SpecialField getSpecialField() {
    return mySpecialField;
  }

  @Nonnull
  @Override
  public DfType getSpecialFieldType() {
    return mySpecialFieldType;
  }

  @Override
  public DfType tryNegate() {
    return new DfGenericObjectType(Set.of(getValue()), TypeConstraints.TOP, DfaNullability.UNKNOWN, Mutability.UNKNOWN,
                                   null, BOTTOM, false);
  }

  @Nonnull
  @Override
  public DfReferenceType dropNullability() {
    return this;
  }

  @Nonnull
  @Override
  public DfType join(@Nonnull DfType other) {
    if (other instanceof DfGenericObjectType || other instanceof DfEphemeralReferenceType) {
      return other.join(this);
    }
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (!(other instanceof DfReferenceType)) return TOP;
    DfReferenceType type = (DfReferenceType)other;
    TypeConstraint constraint = getConstraint().join(type.getConstraint());
    DfaNullability nullability = getNullability().unite(type.getNullability());
    Mutability mutability = getMutability().unite(type.getMutability());
    boolean locality = isLocal() && type.isLocal();
    SpecialField sf = Objects.equals(getSpecialField(), type.getSpecialField()) ? getSpecialField() : null;
    DfType sfType = sf == null ? BOTTOM : getSpecialFieldType().join(type.getSpecialFieldType());
    return new DfGenericObjectType(Set.of(), constraint, nullability, mutability, sf, sfType, locality);
  }
}
