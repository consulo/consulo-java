// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;

import consulo.language.dataFlow.value.RelationType;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiTypes;

public interface DfFloatType extends DfFloatingPointType {
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiTypes.floatType();
  }

  @Override
  default DfType correctForRelationResult(RelationType relation, boolean result) {
    if (result == (relation != RelationType.NE)) {
      return meet(new DfFloatRangeType(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, false, false));
    }
    return this;
  }

  @Override
  default DfType meetRelation(RelationType relationType,
                                       DfType other) {
    DfType result = meet(other.fromRelation(relationType));
    if (result == DfType.BOTTOM && relationType != RelationType.NE) {
      if (!isSuperType(DfTypes.FLOAT_NAN) && other.isSuperType(DfTypes.FLOAT_NAN)) {
        return this;
      }
    }
    return result;
  }
}
