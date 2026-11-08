// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiTypes;

public interface DfBooleanType extends DfPrimitiveType {
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiTypes.booleanType();
  }
}
