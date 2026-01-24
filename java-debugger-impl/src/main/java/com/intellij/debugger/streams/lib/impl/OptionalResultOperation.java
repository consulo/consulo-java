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

package com.intellij.debugger.streams.lib.impl;

import com.intellij.debugger.streams.trace.impl.handler.unified.OptionalTerminationHandler;
import consulo.execution.debug.stream.lib.impl.TerminalOperationBase;
import consulo.execution.debug.stream.resolve.OptionalOrderResolver;
import consulo.execution.debug.stream.trace.impl.interpret.OptionalTraceInterpreter;

/**
 * @author VISTALL
 * @since 2026-01-24
 */
class OptionalResultOperation extends TerminalOperationBase {
    public OptionalResultOperation(String name) {
        super(name,
              (call, expr, dsl) -> new OptionalTerminationHandler(call, expr, dsl),
              new OptionalTraceInterpreter(),
              new OptionalOrderResolver());
    }
}
