// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.types.DfIntegralType;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import consulo.language.dataFlow.value.RelationType;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import org.jspecify.annotations.Nullable;

/**
 * Represents a JVM integral primitive (int or long)
 */
public interface DfJvmIntegralType extends DfIntegralType, DfPrimitiveType {
  /**
   * Cast this type to the specified primitive type
   * @param type target type
   * @return result of the cast
   */
  @Override
  default DfType castTo(PsiPrimitiveType type) {
    if (getConstantOfType(Object.class) != null) {
      return DfPrimitiveType.super.castTo(type);
    }
    if (type.equals(PsiTypes.doubleType()) || type.equals(PsiTypes.floatType())) {
      // No widening support for doubles, so we translate the wide range 
      // to avoid diverged states if (int)(double)x cast is performed
      LongRangeSet range = getWideRange();
      if (range.isEmpty()) return DfType.BOTTOM;
      return type.equals(PsiTypes.doubleType()) ? 
             DfTypes.doubleRange(range.min(), range.max()) :
             DfTypes.floatRange(range.min(), range.max());
    }
    if (!TypeConversionUtil.isIntegralNumberType(type)) {
      return DfPrimitiveType.super.castTo(type);
    }
    LongRangeSet range = JvmPsiRangeSetUtil.castTo(getRange(), type);
    LongRangeSet wideRange = JvmPsiRangeSetUtil.castTo(getWideRange(), type);
    return type.equals(PsiTypes.longType()) ? DfTypes.longRange(range, wideRange) : DfTypes.intRange(range, wideRange);
  }

  @Override
  default DfType fromRelation(RelationType relationType) {
    if (relationType == RelationType.IS || relationType == RelationType.IS_NOT) {
      return DfType.TOP;
    }
    return DfTypes.rangeClamped(getRange().fromRelation(relationType), getLongRangeType());
  }

  @Override
  default boolean isSuperType(DfType other) {
    if (other == DfType.BOTTOM) return true;
    if (!(other instanceof DfIntegralType integralType)) return false;
    if (integralType.getLongRangeType() != getLongRangeType()) return false;
    return getRange().contains(integralType.getRange()) &&
           getWideRange().contains(integralType.getWideRange());
  }

  @Override
  default DfType join(DfType other) {
    if (other == DfType.BOTTOM || other == this) return this;
    if (!(other instanceof DfIntegralType)) return DfType.TOP;
    if (((DfIntegralType)other).getLongRangeType() != getLongRangeType()) return DfType.TOP;
    LongRangeSet range = ((DfIntegralType)other).getRange().join(getRange());
    LongRangeSet wideRange = ((DfIntegralType)other).getWideRange().join(getWideRange());
    return DfTypes.range(range, wideRange, getLongRangeType());
  }

  @Override
  default @Nullable DfType tryJoinExactly(DfType other) {
    if (other == DfType.BOTTOM || other == this) return this;
    if (other == DfType.TOP) return other;
    if (!(other instanceof DfIntegralType)) return null;
    if (((DfIntegralType)other).getLongRangeType() != getLongRangeType()) return null;
    LongRangeSet range = ((DfIntegralType)other).getRange().tryJoinExactly(getRange());
    LongRangeSet wideRange = ((DfIntegralType)other).getWideRange().tryJoinExactly(getWideRange());
    if (range == null || wideRange == null) return null;
    return DfTypes.range(range, wideRange, getLongRangeType());
  }

  @Override
  default DfType meet(DfType other) {
    if (other == DfType.TOP || other == this) return this;
    if (!(other instanceof DfIntegralType)) return DfType.BOTTOM;
    if (((DfIntegralType)other).getLongRangeType() != getLongRangeType()) return DfType.BOTTOM;
    LongRangeSet range = ((DfIntegralType)other).getRange().meet(getRange());
    LongRangeSet wideRange = ((DfIntegralType)other).getWideRange().meet(getWideRange());
    return DfTypes.range(range, wideRange, getLongRangeType());
  }

  @Override
  default DfType meetRange(LongRangeSet range) {
    LongRangeSet resultRange = range.meet(getRange());
    LongRangeSet wideRange = range.meet(getWideRange());
    return DfTypes.range(resultRange, wideRange, getLongRangeType());
  }

  @Override
  default DfType widen() {
    LongRangeSet wideRange = getWideRange();
    return wideRange.equals(getRange()) ? this : DfTypes.range(wideRange, null, getLongRangeType());
  }

  @Override
  default @Nullable DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = getLongRangeType().fullRange().subtract(range);
    return res.intersects(range) ? null : DfTypes.range(res, null, getLongRangeType());
  }
}
