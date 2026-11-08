// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;

import consulo.language.dataFlow.value.RelationType;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiTypes;

public interface DfDoubleType extends DfFloatingPointType {
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiTypes.doubleType();
  }

  @Override
  default DfType correctForRelationResult(RelationType relation, boolean result) {
    if (result == (relation != RelationType.NE)) {
      return meet(new DfDoubleRangeType(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, false));
    }
    return this;
  }

  @Override
  default DfType meetRelation(RelationType relationType,
                                       DfType other) {
    DfType result = meet(other.fromRelation(relationType));
    if (result == DfType.BOTTOM && relationType != RelationType.NE) {
      if (!isSuperType(DfTypes.DOUBLE_NAN) && other.isSuperType(DfTypes.DOUBLE_NAN)) {
        return this;
      }
    }
    return result;
  }
}
