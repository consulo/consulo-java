// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.EvalInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import org.jspecify.annotations.Nullable;

/**
 * Pop array elements from the stack and push the array back
 */
public class FoldArrayInstruction extends EvalInstruction {
  private final DfType myTargetType;

  /**
   * @param anchor   PsiExpression to anchor to
   * @param targetType array type
   * @param elements number of array elements
   */
  public FoldArrayInstruction(@Nullable DfaAnchor anchor,
                              DfType targetType,
                              int elements) {
    super(anchor, elements);
    myTargetType = targetType;
  }

  @Override
  public DfaValue eval(DfaValueFactory factory,
                                DfaMemoryState state,
                                DfaValue ... arguments) {
    for (DfaValue argument : arguments) {
      JavaDfaHelpers.dropLocality(argument, state);
    }
    DfType type = SpecialField.ARRAY_LENGTH.asDfType(myTargetType.meet(DfTypes.NOT_NULL_OBJECT), DfTypes.intValue(arguments.length));
    return factory.fromDfType(type);
  }

  @Override
  public String toString() {
    return "FOLD_ARRAY " + myTargetType;
  }
}
