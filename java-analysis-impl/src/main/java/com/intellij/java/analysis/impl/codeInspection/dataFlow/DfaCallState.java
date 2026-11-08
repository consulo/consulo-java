// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaValue;

public final class DfaCallState {
  final DfaMemoryState myMemoryState;
  final DfaCallArguments myCallArguments;
  final DfaValue myReturnValue;

  public DfaCallState(DfaMemoryState state,
               DfaCallArguments arguments,
               DfaValue returnValue) {
    myMemoryState = state;
    myCallArguments = arguments;
    myReturnValue = returnValue;
  }

  public DfaMemoryState getMemoryState() {
    return myMemoryState;
  }

  public DfaCallArguments getCallArguments() {
    return myCallArguments;
  }

  public DfaValue getReturnValue() {
    return myReturnValue;
  }

  public DfaCallState withMemoryState(DfaMemoryState state) {
    return new DfaCallState(state, myCallArguments, myReturnValue);
  }

  public DfaCallState withArguments(DfaCallArguments arguments) {
    return new DfaCallState(myMemoryState, arguments, myReturnValue);
  }

  public DfaCallState withReturnValue(DfaValue returnValue) {
    return new DfaCallState(myMemoryState, myCallArguments, returnValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DfaCallState that)) return false;
    return myMemoryState.equals(that.myMemoryState) && myCallArguments.equals(that.myCallArguments);
  }

  @Override
  public int hashCode() {
    return 31 * myMemoryState.hashCode() + myCallArguments.hashCode();
  }
}
