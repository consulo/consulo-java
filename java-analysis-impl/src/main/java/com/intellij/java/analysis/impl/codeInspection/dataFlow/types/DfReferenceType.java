// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfNullableType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaDfaNullability;

import consulo.language.dataFlow.types.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.Mutability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import consulo.language.dataFlow.value.DerivedVariableDescriptor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Type that corresponds to JVM reference type; represents subset of possible reference values (may include null)
 */
public interface DfReferenceType extends DfNullableType {
  /**
   * @return nullability of this type
   */
  @Override
  DfaNullability getNullability();

  /**
   * @return type constraint of this type
   */
  TypeConstraint getConstraint();

  /**
   * @return mutability of all the objects referred by this type
   */
  default Mutability getMutability() {
    return Mutability.UNKNOWN;
  }

  /**
   * @return special field if additional information is known about the special field of all referenced objects
   */
  default @Nullable SpecialField getSpecialField() {
    return null;
  }

  /**
   * @return type of special field; {@link DfType#BOTTOM} if {@link #getSpecialField()} returns null
   */
  default DfType getSpecialFieldType() {
    return BOTTOM;
  }

  /**
   * @return this type without type constraint, or simply this type if it's a constant
   */
  default DfReferenceType dropTypeConstraint() {
    return this;
  }

  /**
   * @return this type without locality flag, or simply this type if it's a constant
   */
  default DfReferenceType dropLocality() {
    return this;
  }

  /**
   * @return this type without nullability knowledge, or simply this type if it's a constant
   */
  @Override
  DfReferenceType dropNullability();

  /**
   * @return this type without mutability knowledge, or simply this type if it's a constant
   */
  default DfReferenceType dropMutability() {
    return this;
  }

  /**
   * @return this type without special field knowledge, or simply this type if it's a constant
   */
  default DfReferenceType dropSpecialField() {
    return this;
  }

  @Override
  default DfType correctTypeOnFlush(DfType typeBeforeFlush) {
    DfaNullability inherentNullability = this.getNullability();
    if (inherentNullability == DfaNullability.NULLABLE) {
      DfaNullability nullability = DfaNullability.fromDfType(typeBeforeFlush);
      if (nullability == DfaNullability.FLUSHED || nullability == DfaNullability.NOT_NULL) {
        return dropNullability().meet(JavaDfaNullability.asDfType(DfaNullability.FLUSHED));
      }
    }
    return this;
  }

  @Override
  default DfType correctForClosure() {
    DfReferenceType newType = dropLocality();
    if (newType.getMutability() == Mutability.MUST_NOT_MODIFY) {
      // If we must not modify parameter inside the method body itself,
      // we may still be able to modify it inside nested closures,
      // as they could be executed later.
      newType = newType.dropMutability();
    }
    return newType;
  }

  @Override
  default DfType getBasicType() {
    return dropSpecialField();
  }

  @Override
  default List<DerivedVariableDescriptor> getDerivedVariables() {
    SpecialField field = SpecialField.fromQualifierType(this);
    if (field != null) {
      return List.of(field);
    }
    return List.of();
  }

  @Override
  default Map<DerivedVariableDescriptor, DfType> getDerivedValues() {
    SpecialField field = getSpecialField();
    return field == null ? Map.of() : Map.of(field, getSpecialFieldType());
  }

  @Override
  default boolean mayAlias(DfType otherType) {
    if (isLocal() || otherType.isLocal()) return false;
    if (otherType.meet(this) != BOTTOM) {
      // possible aliasing
      return true;
    }
    if (SpecialField.COLLECTION_SIZE.isMyQualifierType(otherType) &&
        SpecialField.COLLECTION_SIZE.isMyQualifierType(this)) {
      // Collection size is sometimes derived from another (incompatible) collection
      // e.g. keySet Set size is affected by Map size.
      return true;
    }
    return false;
  }

  @Override
  default boolean isImmutableQualifier() {
    return getMutability() == Mutability.UNMODIFIABLE;
  }

  /**
   * @param factory factory to create type constraints
   * @return equivalent reference type where all the constraints are replaced with ones created by a given factory.
   * @see TypeConstraint#convert(TypeConstraints.TypeConstraintFactory) 
   */
  DfReferenceType convert(TypeConstraints.TypeConstraintFactory factory);

  @Override
  String toString();
}
