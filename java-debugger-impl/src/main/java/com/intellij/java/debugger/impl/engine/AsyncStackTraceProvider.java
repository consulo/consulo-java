// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Allows to replace a part of the debugger call stack with the "async" part:
 * every frame is asked for async stack trace via {@link #getAsyncStackTrace(JavaStackFrame, SuspendContextImpl)}
 * and if it returns something - it replaces the rest of the stack.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface AsyncStackTraceProvider {
    ExtensionPointName<AsyncStackTraceProvider> EP = ExtensionPointName.create(AsyncStackTraceProvider.class);

    @Nullable List<StackFrameItem> getAsyncStackTrace(JavaStackFrame stackFrame, SuspendContextImpl suspendContext);
}
