// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.types.DfEphemeralType;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.Mutability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import consulo.language.dataFlow.memory.DfaMemoryState;
import org.jspecify.annotations.Nullable;

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
public class DfEphemeralReferenceType implements DfEphemeralType, DfReferenceType {
  private final TypeConstraint myTypeConstraint;

  DfEphemeralReferenceType(TypeConstraint constraint) {
    myTypeConstraint = constraint;
  }

  @Override
  public DfaNullability getNullability() {
    return DfaNullability.NOT_NULL;
  }

  @Override
  public TypeConstraint getConstraint() {
    return myTypeConstraint;
  }

  @Override
  public DfReferenceType dropNullability() {
    return this;
  }

  @Override
  public DfReferenceType convert(TypeConstraints.TypeConstraintFactory factory) {
    return new DfEphemeralReferenceType(myTypeConstraint.convert(factory));
  }

  @Override
  public DfReferenceType dropTypeConstraint() {
    return DfTypes.NOT_NULL_OBJECT;
  }

  @Override
  public boolean isSuperType(DfType other) {
    if (other == DfType.BOTTOM) return true;
    if (other instanceof DfEphemeralReferenceType) {
      return myTypeConstraint.isSuperConstraintOf(((DfEphemeralReferenceType)other).myTypeConstraint);
    }
    return false;
  }

  @Override
  public DfType join(DfType other) {
    if (other == DfType.BOTTOM) return this;
    if (other == DfType.TOP || !(other instanceof DfReferenceType)) return DfType.TOP;
    TypeConstraint otherConstraint = ((DfReferenceType)other).getConstraint();
    TypeConstraint constraint = myTypeConstraint.join(otherConstraint);
    if (other instanceof DfEphemeralReferenceType) {
      return constraint == myTypeConstraint ? this :
             constraint == otherConstraint ? other :
             constraint == TypeConstraints.TOP ? DfTypes.NOT_NULL_OBJECT :
             new DfEphemeralReferenceType(constraint);
    }
    Set<Object> notValues = other instanceof DfGenericObjectType ? ((DfGenericObjectType)other).getRawNotValues() : Set.of();
    return new DfGenericObjectType(notValues, constraint, ((DfReferenceType)other).getNullability(),
                                   Mutability.UNKNOWN, null, DfType.BOTTOM, false);
  }

  @Override
  public @Nullable DfType tryJoinExactly(DfType other) {
    if (other == DfType.BOTTOM) return this;
    if (other == DfType.TOP) return other;
    if (other instanceof DfEphemeralReferenceType) {
      TypeConstraint otherConstraint = ((DfEphemeralReferenceType)other).getConstraint();
      TypeConstraint constraint = myTypeConstraint.tryJoinExactly(otherConstraint);
      return constraint == null ? null :
             constraint == myTypeConstraint ? this :
             constraint == otherConstraint ? other :
             new DfEphemeralReferenceType(constraint);
    }
    return null;
  }

  @Override
  public DfType meet(DfType other) {
    if (other == DfType.TOP) return this;
    if (other == DfType.BOTTOM) return other;
    if (other instanceof DfEphemeralReferenceType ||
        other instanceof DfGenericObjectType) {
      TypeConstraint otherConstraint = ((DfReferenceType)other).getConstraint();
      TypeConstraint constraint = myTypeConstraint.meet(otherConstraint);
      return constraint == myTypeConstraint ? this :
             constraint == otherConstraint ? other :
             constraint == TypeConstraints.BOTTOM ? DfType.BOTTOM :
             new DfEphemeralReferenceType(constraint);
    }
    return DfType.BOTTOM;
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
    return "ephemeral " + getConstraint();
  }
}
