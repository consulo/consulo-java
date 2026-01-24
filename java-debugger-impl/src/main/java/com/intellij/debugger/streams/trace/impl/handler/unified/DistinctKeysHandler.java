// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified;

import consulo.execution.debug.stream.trace.dsl.Dsl;
import consulo.execution.debug.stream.trace.impl.handler.unified.DistinctByKeyHandler;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class DistinctKeysHandler extends DistinctByKeyHandler.DistinctByCustomKey {
    public DistinctKeysHandler(int callNumber, @Nonnull IntermediateStreamCall call, @Nonnull Dsl dsl) {
        super(callNumber, call,
              "java.util.function.Function<java.util.Map.Entry, java.lang.Object>",
              dsl.lambda("x", lambdaBody -> {
                  lambdaBody.doReturn(lambdaBody.getLambdaArg().call("getKey"));
              }).toCode(),
              dsl);
    }
}

