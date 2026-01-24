// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl;

import consulo.execution.debug.stream.lib.impl.*;
import consulo.execution.debug.stream.trace.impl.handler.unified.DistinctTraceHandler;
import consulo.execution.debug.stream.trace.impl.interpret.AllMatchTraceInterpreter;
import consulo.execution.debug.stream.trace.impl.interpret.AnyMatchTraceInterpreter;
import consulo.execution.debug.stream.trace.impl.interpret.NoneMatchTraceInterpreter;

/**
 * @author Vitaliy.Bibaev
 */
public class StandardLibrarySupport extends LibrarySupportBase {
    public StandardLibrarySupport() {
        addIntermediateOperationsSupport(
            new FilterOperation("filter"),
            new FilterOperation("limit"),
            new FilterOperation("skip"),
            new FilterOperation("peek"),
            new FilterOperation("onClose"),
            new MappingOperation("map"),
            new MappingOperation("mapToInt"),
            new MappingOperation("mapToLong"),
            new MappingOperation("mapToDouble"),
            new MappingOperation("mapToObj"),
            new MappingOperation("boxed"),
            new FlatMappingOperation("flatMap"),
            new FlatMappingOperation("flatMapToInt"),
            new FlatMappingOperation("flatMapToLong"),
            new FlatMappingOperation("flatMapToDouble"),
            new DistinctOperation("distinct", (num, call, dsl) -> new DistinctTraceHandler(num, call, dsl)),
            new SortedOperation("sorted"),
            new ParallelOperation("parallel")
        );

        addTerminationOperationsSupport(
            new MatchingOperation("anyMatch", new AnyMatchTraceInterpreter()),
            new MatchingOperation("allMatch", new AllMatchTraceInterpreter()),
            new MatchingOperation("noneMatch", new NoneMatchTraceInterpreter()),
            new OptionalResultOperation("min"),
            new OptionalResultOperation("max"),
            new OptionalResultOperation("findAny"),
            new OptionalResultOperation("findFirst"),
            new ToCollectionOperation("toArray"),
            new ToCollectionOperation("toList"),
            new ToCollectionOperation("collect")
        );
    }
}
