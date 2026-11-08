// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors;

import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.VariableDescriptor;

public abstract class JvmVariableDescriptor implements VariableDescriptor {
  @Override
  public boolean alwaysEqualsToItself(DfType type) {
    return !type.isSuperType(DfTypes.FLOAT_NAN) &&
           !type.isSuperType(DfTypes.DOUBLE_NAN);
  }
}
