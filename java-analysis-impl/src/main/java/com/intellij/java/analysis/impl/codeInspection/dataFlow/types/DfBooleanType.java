// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import javax.annotation.Nonnull;

public interface DfBooleanType extends DfPrimitiveType {
  @Nonnull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.BOOLEAN;
  }
}
