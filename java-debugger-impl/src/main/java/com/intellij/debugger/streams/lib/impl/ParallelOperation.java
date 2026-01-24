// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl;

import com.intellij.debugger.streams.trace.impl.handler.unified.ParallelHandler;
import consulo.execution.debug.stream.lib.impl.IntermediateOperationBase;
import consulo.execution.debug.stream.resolve.FilterResolver;
import consulo.execution.debug.stream.trace.impl.interpret.SimplePeekCallTraceInterpreter;

public class ParallelOperation extends IntermediateOperationBase {
    public ParallelOperation(String name) {
        super(name,
              (num, call, dsl) -> new ParallelHandler(num, call, dsl),
              new SimplePeekCallTraceInterpreter(),
              new FilterResolver());
    }
}
