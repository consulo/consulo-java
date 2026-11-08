// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiTypes;
import org.jspecify.annotations.Nullable;

final class DfLongRangeType extends DfAbstractRangeType implements DfLongType {
  DfLongRangeType(LongRangeSet range, @Nullable LongRangeSet wideRange) {
    super(range, wideRange);
  }

  @Override
  public String toString() {
    if (getRange() == LongRangeSet.all()) return PsiKeyword.LONG;
    return PsiKeyword.LONG + " " + JvmPsiRangeSetUtil.getPresentationText(getRange(), PsiTypes.longType());
  }
}
