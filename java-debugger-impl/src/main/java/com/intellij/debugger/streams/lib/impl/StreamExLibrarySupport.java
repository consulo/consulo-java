// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl;

import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctKeysHandler;
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctValuesHandler;
import consulo.execution.debug.stream.lib.IntermediateOperation;
import consulo.execution.debug.stream.lib.impl.*;
import consulo.execution.debug.stream.resolve.AppendResolver;
import consulo.execution.debug.stream.resolve.IntervalMapResolver;
import consulo.execution.debug.stream.resolve.PairMapResolver;
import consulo.execution.debug.stream.resolve.PrependResolver;
import consulo.execution.debug.stream.trace.impl.handler.unified.DistinctByKeyHandler;
import consulo.execution.debug.stream.trace.impl.handler.unified.DistinctTraceHandler;

import java.util.Arrays;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamExLibrarySupport extends LibrarySupportBase {
    public StreamExLibrarySupport() {
        super(new StandardLibrarySupport());

        addIntermediateOperationsSupport(filterOperations(
            "atLeast", "atMost", "less", "greater", "filterBy", "filterKeys", "filterValues", "filterKeyValue",
            "nonNull", "nonNullKeys", "nonNullValues", "remove", "removeBy", "removeKeys", "removeValues", "removeKeyValue",
            "select", "selectKeys", "selectValues", "dropWhile", "takeWhile", "takeWhileInclusive", "skipOrdered",
            "without", "peekFirst", "peekLast", "peekKeys", "peekValues", "peekKeyValue"));

        addIntermediateOperationsSupport(mapOperations(
            "mapFirst", "mapFirstOrElse", "mapLast", "mapLastOrElse",
            "keys", "values", "mapKeyValue", "mapKeys", "mapValues", "mapToEntry", "mapToKey", "mapToValue",
            "elements", "invert", "join", "withFirst", "zipWith"));

        addIntermediateOperationsSupport(flatMapOperations(
            "flatMapToInt", "flatMapToLong", "flatMapToDouble", "flatMapToObj", "flatMapToEntry", "cross",
            "flatMapToKey", "flatMapToValue", "flatMapKeys", "flatMapValues", "flatMapKeyValue", "flatArray", "flatCollection"));

        addIntermediateOperationsSupport(sortedOperations("sortedBy", "sortedByInt", "sortedByDouble", "sortedByLong", "reverseSorted"));

        addIntermediateOperationsSupport(
            new DistinctOperation("distinct", (num, call, dsl) -> {
                var arguments = call.getArguments();
                if (arguments.isEmpty() || "int".equals(arguments.get(0).getType())) {
                    return new DistinctTraceHandler(num, call, dsl);
                }
                return new DistinctByKeyHandler(num, call, dsl);
            }),
            new DistinctOperation("distinctKeys", DistinctKeysHandler::new),
            new DistinctOperation("distinctValues", DistinctValuesHandler::new)
        );

        addIntermediateOperationsSupport(new ConcatOperation("append", new AppendResolver()),
                                         new ConcatOperation("prepend", new PrependResolver()));

        addIntermediateOperationsSupport(collapseOperations("collapse", "collapseKeys", "runLengths", "groupRuns"));

        addIntermediateOperationsSupport(new OrderBasedOperation("pairMap", new PairMapResolver()),
                                         new OrderBasedOperation("intervalMap", new IntervalMapResolver()));
        addTerminationOperationsSupport();
    }

    private static IntermediateOperation[] filterOperations(String... names) {
        return Arrays.stream(names).map(FilterOperation::new).toArray(IntermediateOperation[]::new);
    }

    private static IntermediateOperation[] mapOperations(String... names) {
        return Arrays.stream(names).map(MappingOperation::new).toArray(IntermediateOperation[]::new);
    }

    private static IntermediateOperation[] flatMapOperations(String... names) {
        return Arrays.stream(names).map(FlatMappingOperation::new).toArray(IntermediateOperation[]::new);
    }

    private static IntermediateOperation[] sortedOperations(String... names) {
        return Arrays.stream(names).map(SortedOperation::new).toArray(IntermediateOperation[]::new);
    }

    private static IntermediateOperation[] collapseOperations(String... names) {
        return Arrays.stream(names).map(CollapseOperation::new).toArray(IntermediateOperation[]::new);
    }
}
