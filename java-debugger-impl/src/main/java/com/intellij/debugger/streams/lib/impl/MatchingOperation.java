// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl;

import com.intellij.debugger.streams.trace.impl.handler.unified.MatchHandler;
import consulo.execution.debug.stream.lib.impl.TerminalOperationBase;
import consulo.execution.debug.stream.resolve.AllToResultResolver;
import consulo.execution.debug.stream.trace.CallTraceInterpreter;

/**
 * @author Vitaliy.Bibaev
 */
public class MatchingOperation extends TerminalOperationBase {
    public MatchingOperation(String name, CallTraceInterpreter interpreter) {
        super(name,
              (call, expr, dsl) -> new MatchHandler(call, dsl),
              interpreter,
              new AllToResultResolver());
    }
}

