/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.debugger.streams.trace.impl.handler.unified;

import consulo.execution.debug.stream.trace.dsl.Dsl;
import consulo.execution.debug.stream.trace.impl.handler.unified.DistinctByKeyHandler;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import jakarta.annotation.Nonnull;

public class DistinctValuesHandler extends DistinctByKeyHandler.DistinctByCustomKey {
    public DistinctValuesHandler(int callNumber, @Nonnull IntermediateStreamCall call, @Nonnull Dsl dsl) {
        super(callNumber, call,
            "java.util.function.Function<java.util.Map.Entry, java.lang.Object>",
            dsl.lambda("x", lambdaBody -> {
                lambdaBody.doReturn(lambdaBody.getLambdaArg().call("getValue"));
            }).toCode(),
            dsl);
    }
}
