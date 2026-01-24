// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified;

import consulo.document.util.TextRange;
import consulo.execution.debug.stream.trace.dsl.Dsl;
import consulo.execution.debug.stream.trace.impl.handler.type.GenericType;
import consulo.execution.debug.stream.trace.impl.handler.unified.PeekTraceHandler;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import consulo.execution.debug.stream.wrapper.impl.IntermediateStreamCallImpl;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ParallelHandler extends PeekTraceHandler {
    private final IntermediateStreamCall call;

    public ParallelHandler(int num, @Nonnull IntermediateStreamCall call, @Nonnull Dsl dsl) {
        super(num, call.getName(), call.getTypeBefore(), call.getTypeAfter(), dsl);
        this.call = call;
    }

    @Nonnull
    @Override
    public List<IntermediateStreamCall> additionalCallsAfter() {
        List<IntermediateStreamCall> calls = new ArrayList<>(super.additionalCallsAfter());
        calls.add(0, new SequentialCall(call.getTypeBefore()));
        return calls;
    }

    private static class SequentialCall extends IntermediateStreamCallImpl {
        SequentialCall(@Nonnull GenericType elementsType) {
            super("sequential", "", Collections.emptyList(), elementsType, elementsType, TextRange.EMPTY_RANGE);
        }
    }
}
