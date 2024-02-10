// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstant;
import one.util.streamex.StreamEx;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.BOTTOM;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.TOP;

class DfGenericObjectType extends DfAntiConstantType<Object> implements DfReferenceType {
  private final
  @Nonnull
  TypeConstraint myConstraint;
  private final
  @Nonnull
  DfaNullability myNullability;
  private final
  @Nonnull
  Mutability myMutability;
  private final
  @Nullable
  SpecialField mySpecialField;
  private final
  @Nonnull
  DfType mySpecialFieldType;
  private final boolean myLocal;

  DfGenericObjectType(@Nonnull Set<Object> notValues,
                      @Nonnull TypeConstraint constraint,
                      @Nonnull DfaNullability nullability,
                      @Nonnull Mutability mutability,
                      @Nullable SpecialField field,
                      @Nonnull DfType type,
                      boolean local) {
    super(notValues);
    assert constraint != TypeConstraints.BOTTOM;
    myConstraint = constraint;
    myNullability = nullability;
    myMutability = mutability;
    mySpecialField = field;
    mySpecialFieldType = type instanceof DfReferenceType ? ((DfReferenceType) type).dropSpecialField() : type;
    myLocal = local;
  }

  @Nonnull
  @Override
  public DfaNullability getNullability() {
    return myNullability;
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

  @Override
  public boolean isLocal() {
    return myLocal;
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
    if (myMutability != Mutability.UNKNOWN || myLocal || mySpecialField != null) {
      return null;
    }
    TypeConstraint negated = myConstraint.tryNegate();
    if (negated == null) {
      return null;
    }
    DfType result = negated.asDfType();
    return myNullability == DfaNullability.NOT_NULL ? result.join(DfTypes.NULL) : result.meet(DfTypes.NOT_NULL_OBJECT);
  }

  @Nonnull
  @Override
  public Set<Object> getNotValues() {
    if (myNullability == DfaNullability.NOT_NULL) {
      Set<Object> values = new HashSet<>(myNotValues);
      values.add(null);
      return Collections.unmodifiableSet(values);
    }
    return super.getNotValues();
  }

  @Nonnull
  @Override
  public DfReferenceType dropTypeConstraint() {
    return myConstraint == TypeConstraints.TOP ? this :
        new DfGenericObjectType(myNotValues, TypeConstraints.TOP, myNullability, myMutability, mySpecialField, mySpecialFieldType,
            myLocal);
  }

  @Nonnull
  @Override
  public DfReferenceType dropMutability() {
    return myMutability == Mutability.UNKNOWN ? this :
        new DfGenericObjectType(myNotValues, myConstraint, myNullability, Mutability.UNKNOWN, mySpecialField, mySpecialFieldType,
            myLocal);
  }

  @Nonnull
  @Override
  public DfReferenceType dropLocality() {
    return myLocal ? new DfGenericObjectType(myNotValues, myConstraint, myNullability, myMutability, mySpecialField, mySpecialFieldType,
        false) : this;
  }

  @Nonnull
  @Override
  public DfReferenceType dropNullability() {
    return myNullability == DfaNullability.UNKNOWN ? this :
        new DfGenericObjectType(myNotValues, myConstraint, DfaNullability.UNKNOWN, myMutability, mySpecialField, mySpecialFieldType,
            myLocal);
  }

  @Nonnull
  @Override
  public DfReferenceType dropSpecialField() {
    return mySpecialField == null ? this :
        new DfGenericObjectType(myNotValues, myConstraint, myNullability, myMutability, null, BOTTOM, myLocal);
  }

  @Override
  public boolean isSuperType(@Nonnull DfType other) {
    if (other == BOTTOM) {
      return true;
    }
    if (other instanceof DfNullConstantType) {
      return getNullability() != DfaNullability.NOT_NULL;
    }
    if (!(other instanceof DfReferenceType)) {
      return false;
    }
    if (!myNotValues.isEmpty()) {
      if (other instanceof DfReferenceConstantType) {
        if (myNotValues.contains(((DfReferenceConstantType) other).getValue())) {
          return false;
        }
      } else if (other instanceof DfGenericObjectType) {
        if (!((DfGenericObjectType) other).myNotValues.containsAll(myNotValues)) {
          return false;
        }
      } else {
        return false;
      }
    }
    DfReferenceType type = (DfReferenceType) other;
    if (isLocal() && !type.isLocal()) {
      return false;
    }
    if (type.getNullability() != getNullability() && getNullability() != DfaNullability.UNKNOWN &&
        type.getNullability() != DfaNullability.NOT_NULL) {
      return false;
    }
    if (!getConstraint().isSuperConstraintOf(type.getConstraint())) {
      return false;
    }
    if (getMutability().ordinal() > type.getMutability().ordinal()) {
      return false;
    }
    SpecialField sf = getSpecialField();
    if (sf != null) {
      if (sf != type.getSpecialField()) {
        return false;
      }
      if (!getSpecialFieldType().isSuperType(type.getSpecialFieldType())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isMergeable(@Nonnull DfType other) {
    if (!isSuperType(other)) {
      return false;
    }
    if (getNullability() == DfaNullability.UNKNOWN) {
      DfaNullability otherNullability = DfaNullability.fromDfType(other);
      return otherNullability != DfaNullability.NULL && otherNullability != DfaNullability.NULLABLE;
    }
    return true;
  }

  @Nonnull
  @Override
  public DfType join(@Nonnull DfType other) {
    if (isSuperType(other)) {
      return this;
    }
    if (other.isSuperType(this)) {
      return other;
    }
    if (!(other instanceof DfReferenceType)) {
      return TOP;
    }
    if (other instanceof DfNullConstantType || other instanceof DfEphemeralReferenceType) {
      return other.join(this);
    }
    DfReferenceType type = (DfReferenceType) other;
    TypeConstraint constraint = getConstraint().join(type.getConstraint());
    DfaNullability nullability = getNullability().unite(type.getNullability());
    Mutability mutability = getMutability().unite(type.getMutability());
    boolean locality = isLocal() && type.isLocal();
    SpecialField sf = Objects.equals(getSpecialField(), type.getSpecialField()) ? getSpecialField() : null;
    DfType sfType = sf == null ? BOTTOM : getSpecialFieldType().join(type.getSpecialFieldType());
    Set<Object> notValues = myNotValues;
    if (type instanceof DfGenericObjectType) {
      notValues = new HashSet<>(myNotValues);
      notValues.retainAll(((DfGenericObjectType) other).myNotValues);
    }
    if (type instanceof DfReferenceConstantType) {
      notValues = new HashSet<>(myNotValues);
      notValues.remove(((DfReferenceConstantType) type).getValue());
    }
    return new DfGenericObjectType(notValues, constraint, nullability, mutability, sf, sfType, locality);
  }

  @Nonnull
  @Override
  public DfType meet(@Nonnull DfType other) {
    if (other instanceof DfConstantType || other instanceof DfEphemeralReferenceType) {
      return other.meet(this);
    }
    if (isSuperType(other)) {
      return other;
    }
    if (other.isSuperType(this)) {
      return this;
    }
    if (!(other instanceof DfReferenceType)) {
      return BOTTOM;
    }
    DfReferenceType type = (DfReferenceType) other;
    TypeConstraint constraint = getConstraint().meet(type.getConstraint());
    if (constraint == TypeConstraints.BOTTOM) {
      return isSuperType(DfTypes.NULL) && other.isSuperType(DfTypes.NULL) ? DfTypes.NULL : BOTTOM;
    }
    DfaNullability nullability = getNullability().intersect(type.getNullability());
    if (nullability == null) {
      return BOTTOM;
    }
    Mutability mutability = getMutability().intersect(type.getMutability());
    boolean locality = isLocal() || type.isLocal();
    SpecialField sf;
    DfType sfType;
    if (getSpecialField() == null) {
      sf = type.getSpecialField();
      sfType = type.getSpecialFieldType();
    } else if (type.getSpecialField() == null) {
      sf = getSpecialField();
      sfType = getSpecialFieldType();
    } else {
      sf = getSpecialField();
      if (sf != type.getSpecialField()) {
        return BOTTOM;
      }
      sfType = sf == null ? BOTTOM : getSpecialFieldType().meet(type.getSpecialFieldType());
    }
    if (sf != null && sfType == BOTTOM) {
      return BOTTOM;
    }
    Set<Object> notValues = myNotValues;
    if (type instanceof DfGenericObjectType) {
      Set<Object> otherNotValues = ((DfGenericObjectType) other).myNotValues;
      if (otherNotValues.containsAll(myNotValues)) {
        notValues = otherNotValues;
      } else if (!myNotValues.containsAll(otherNotValues)) {
        notValues = new HashSet<>(myNotValues);
        notValues.addAll(otherNotValues);
        if (nullability == DfaNullability.NOT_NULL) {
          DfEphemeralReferenceType ephemeralValue = checkEphemeral(constraint, notValues);
          if (ephemeralValue != null) {
            return ephemeralValue;
          }
        }
      }
    }
    return new DfGenericObjectType(notValues, constraint, nullability, mutability, sf, sfType, locality);
  }

  private static DfEphemeralReferenceType checkEphemeral(TypeConstraint constraint, Set<Object> notValues) {
    if (notValues.isEmpty()) {
      return null;
    }
    Object value = notValues.iterator().next();
    if (!(value instanceof PsiEnumConstant)) {
      return null;
    }
    PsiClass enumClass = ((PsiEnumConstant) value).getContainingClass();
    if (enumClass == null) {
      return null;
    }
    TypeConstraint enumType = TypeConstraints.instanceOf(
        JavaPsiFacade.getElementFactory(enumClass.getProject()).createType(enumClass));
    if (!enumType.equals(constraint)) {
      return null;
    }
    Set<PsiEnumConstant> allEnumConstants = StreamEx.of(enumClass.getFields()).select(PsiEnumConstant.class).toSet();
    if (notValues.size() != allEnumConstants.size()) {
      return null;
    }
    for (Object notValue : notValues) {
      if (!(notValue instanceof PsiEnumConstant)) {
        return null;
      }
      if (!allEnumConstants.remove(notValue)) {
        return null;
      }
    }
    if (allEnumConstants.isEmpty()) {
      return new DfEphemeralReferenceType(constraint);
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DfGenericObjectType type = (DfGenericObjectType) o;
    return myLocal == type.myLocal &&
        myNullability == type.myNullability &&
        myMutability == type.myMutability &&
        mySpecialField == type.mySpecialField &&
        myConstraint.equals(type.myConstraint) &&
        mySpecialFieldType.equals(type.mySpecialFieldType) &&
        myNotValues.equals(type.myNotValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myConstraint, myNullability, myMutability, mySpecialField, mySpecialFieldType, myLocal, myNotValues);
  }

  @Override
  public String toString() {
    List<String> components = new ArrayList<>();
    if (myConstraint != TypeConstraints.TOP) {
      components.add(myConstraint.toString());
    }
    if (myNullability != DfaNullability.UNKNOWN) {
      components.add(myNullability.toString());
    }
    if (myMutability != Mutability.UNKNOWN) {
      components.add(myMutability.name());
    }
    if (myLocal) {
      components.add(JavaAnalysisBundle.message("type.information.local.object"));
    }
    if (mySpecialField != null) {
      components.add(mySpecialField + "=" + mySpecialFieldType);
    }
    if (!myNotValues.isEmpty()) {
      components.add("!= " + StreamEx.of(myNotValues).map(DfConstantType::renderValue).joining(", "));
    }
    return String.join(" ", components);
  }
}
