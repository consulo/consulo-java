// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.BOTTOM;

/**
 * Type that corresponds to JVM reference type; represents subset of possible reference values (may include null)
 */
public interface DfReferenceType extends DfType {
  /**
   * @return nullability of this type
   */
  @Nonnull
  DfaNullability getNullability();

  /**
   * @return type constraint of this type
   */
  @Nonnull
  TypeConstraint getConstraint();

  /**
   * @return mutability of all the objects referred by this type
   */
  @Nonnull
  default Mutability getMutability() {
    return Mutability.UNKNOWN;
  }

  /**
   * @return true if this type contains references only to local objects (not leaked from the current context to unknown methods)
   */
  default boolean isLocal() {
    return false;
  }

  /**
   * @return special field if additional information is known about the special field of all referenced objects
   */
  @Nullable
  default SpecialField getSpecialField() {
    return null;
  }

  /**
   * @return type of special field; {@link DfTypes#BOTTOM} if {@link #getSpecialField()} returns null
   */
  @Nonnull
  default DfType getSpecialFieldType() {
    return BOTTOM;
  }

  /**
   * @return this type without type constraint, or simply this type if it's a constant
   */
  @Nonnull
  default DfReferenceType dropTypeConstraint() {
    return this;
  }

  /**
   * @return this type without locality flag, or simply this type if it's a constant
   */
  @Nonnull
  default DfReferenceType dropLocality() {
    return this;
  }

  /**
   * @return this type without nullability knowledge, or simply this type if it's a constant
   */
  @Nonnull
  DfReferenceType dropNullability();

  /**
   * @return this type without mutability knowledge, or simply this type if it's a constant
   */
  @Nonnull
  default DfReferenceType dropMutability() {
    return this;
  }

  /**
   * @return this type without special field knowledge, or simply this type if it's a constant
   */
  default DfReferenceType dropSpecialField() {
    return this;
  }

  /**
   * @param type type to check
   * @return true if the supplied type is a reference type that contains references only
   * to local objects (not leaked from the current context to unknown methods)
   */
  static boolean isLocal(DfType type) {
    return type instanceof DfReferenceType && ((DfReferenceType) type).isLocal();
  }

  /**
   * @param type type to drop
   * @return this type dropping any relation to the supplied type
   */
  @Nonnull
  default DfType withoutType(@Nonnull TypeConstraint type) {
    TypeConstraint constraint = getConstraint();
    if (constraint.equals(type)) {
      return dropTypeConstraint();
    }
    if (type instanceof TypeConstraint.Exact) {
      TypeConstraint.Exact exact = (TypeConstraint.Exact) type;
      TypeConstraint result = TypeConstraints.TOP;
      result = constraint.instanceOfTypes().without(exact).map(TypeConstraint.Exact::instanceOf)
          .foldLeft(result, TypeConstraint::meet);
      result = constraint.notInstanceOfTypes().without(exact).map(TypeConstraint.Exact::notInstanceOf)
          .foldLeft(result, TypeConstraint::meet);
      return dropTypeConstraint().meet(result.asDfType());
    }
    return this;
  }

  @Override
  String toString();
}
