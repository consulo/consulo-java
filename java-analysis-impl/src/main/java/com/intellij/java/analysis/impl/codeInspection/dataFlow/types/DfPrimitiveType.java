// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfNotNullableType;
import consulo.language.dataFlow.types.DfType;

import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.util.TypeConversionUtil;

/**
 * A type that represents concrete JVM primitive type or subset of values of given type
 */
public interface DfPrimitiveType extends DfNotNullableType {
  PsiPrimitiveType getPsiType();

  /**
   * Cast this type to the specified primitive type
   * @param type target type
   * @return result of the cast
   */
  default DfType castTo(PsiPrimitiveType type) {
    Object value = TypeConversionUtil.computeCastTo(getConstantOfType(Object.class), type);
    if (value != null) {
      return DfTypes.primitiveConstant(value);
    }
    return DfType.TOP;
  }
}
