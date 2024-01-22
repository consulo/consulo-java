// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * A reference value that is impossible with currently available source code but
 * could appear if separate compilation takes place. A common example of ephemeral value
 * is a constant of enum type that doesn't equal to any existing constant.
 * <p>
 * When ephemeral value is stored in the memory state variable we assume that the whole
 * memory state is ephemeral.
 * 
 * @see DfaMemoryState#isEphemeral()
 */
public class DfEphemeralReferenceType implements DfReferenceType {
  private final @Nonnull
  TypeConstraint myTypeConstraint;

  DfEphemeralReferenceType(@jakarta.annotation.Nonnull TypeConstraint constraint) {
    myTypeConstraint = constraint;
  }

  @Override
  public @Nonnull
  DfaNullability getNullability() {
    return DfaNullability.NOT_NULL;
  }

  @Override
  public @Nonnull
  TypeConstraint getConstraint() {
    return myTypeConstraint;
  }

  @Override
  public @Nonnull
  DfReferenceType dropNullability() {
    return this;
  }

  @Override
  public @Nonnull
  DfReferenceType dropTypeConstraint() {
    return DfTypes.NOT_NULL_OBJECT;
  }

  @Override
  public boolean isSuperType(@Nonnull DfType other) {
    if (other == DfTypes.BOTTOM) return true;
    if (other instanceof DfEphemeralReferenceType) {
      return myTypeConstraint.isSuperConstraintOf(((DfEphemeralReferenceType)other).myTypeConstraint);
    }
    return false;
  }

  @Override
  public @Nonnull
  DfType join(@Nonnull DfType other) {
    if (other == DfTypes.BOTTOM) return this;
    if (other == DfTypes.TOP || !(other instanceof DfReferenceType)) return DfTypes.TOP;
    TypeConstraint otherConstraint = ((DfReferenceType)other).getConstraint();
    TypeConstraint constraint = myTypeConstraint.join(otherConstraint);
    if (other instanceof DfEphemeralReferenceType) {
      return constraint == myTypeConstraint ? this :
             constraint == otherConstraint ? other :
             constraint == TypeConstraints.TOP ? DfTypes.NOT_NULL_OBJECT :
             new DfEphemeralReferenceType(constraint);
    }
    Set<Object> notValues = other instanceof DfGenericObjectType ? ((DfGenericObjectType)other).myNotValues : Set.of();
    return new DfGenericObjectType(notValues, constraint, ((DfReferenceType)other).getNullability(),
                                   Mutability.UNKNOWN, null, DfTypes.BOTTOM, false);
  }

  @Override
  public @jakarta.annotation.Nonnull
  DfType meet(@Nonnull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (other == DfTypes.BOTTOM) return other;
    if (other instanceof DfEphemeralReferenceType ||
        other instanceof DfGenericObjectType) {
      TypeConstraint otherConstraint = ((DfReferenceType)other).getConstraint();
      TypeConstraint constraint = myTypeConstraint.meet(otherConstraint);
      return constraint == myTypeConstraint ? this :
             constraint == otherConstraint ? other :
             constraint == TypeConstraints.BOTTOM ? DfTypes.BOTTOM :
             new DfEphemeralReferenceType(constraint);
    }
    return DfTypes.BOTTOM;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof DfEphemeralReferenceType && myTypeConstraint.equals(((DfEphemeralReferenceType)o).myTypeConstraint);
  }

  @Override
  public int hashCode() {
    return myTypeConstraint.hashCode();
  }

  @Override
  public String toString() {
    return "ephemeral " + getConstraint().toString();
  }
}
