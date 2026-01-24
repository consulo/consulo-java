// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl;

import consulo.execution.debug.stream.lib.impl.*;
import consulo.execution.debug.stream.resolve.AppendResolver;
import consulo.execution.debug.stream.resolve.FilteredMapResolver;
import consulo.execution.debug.stream.trace.impl.handler.unified.DistinctByKeyHandler;
import consulo.execution.debug.stream.trace.impl.handler.unified.DistinctTraceHandler;

import java.util.Arrays;

/**
 * @author Vitaliy.Bibaev
 */
public class JBIterableSupport extends LibrarySupportBase {
    public JBIterableSupport() {
        addIntermediateOperationsSupport(filterOperations("filter", "skip", "skipWhile", "take", "takeWhile"));
        addIntermediateOperationsSupport(mapOperations("map", "transform"));
        addIntermediateOperationsSupport(new FlatMappingOperation("flatMap"),
                                         new FlatMappingOperation("flatten"));

        addIntermediateOperationsSupport(new DistinctOperation("unique", (num, call, dsl) -> {
            if (call.getArguments().isEmpty()) {
                return new DistinctTraceHandler(num, call, dsl);
            }
            return new DistinctByKeyHandler(num, call, dsl, "fun", 0, dsl.getTypes().ANY(), dsl.getTypes().ANY());
        }));

        addIntermediateOperationsSupport(new ConcatOperation("append", new AppendResolver()));

        addIntermediateOperationsSupport(new SortedOperation("sorted"), new SortedOperation("collect"));

        addIntermediateOperationsSupport(new OrderBasedOperation("filterMap", new FilteredMapResolver()));
    }

    public static FilterOperation[] filterOperations(String... names) {
        return Arrays.stream(names).map(FilterOperation::new).toArray(FilterOperation[]::new);
    }

    public static MappingOperation[] mapOperations(String... names) {
        return Arrays.stream(names).map(MappingOperation::new).toArray(MappingOperation[]::new);
    }
}
