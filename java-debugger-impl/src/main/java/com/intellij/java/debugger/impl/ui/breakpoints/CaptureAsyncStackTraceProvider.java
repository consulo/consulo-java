// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.impl.engine.AsyncStackTraceProvider;
import com.intellij.java.debugger.impl.engine.JavaStackFrame;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import consulo.annotation.component.ExtensionImpl;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Top-level counterpart of JetBrains' nested {@code StackCapturingLineBreakpoint.CaptureAsyncStackTraceProvider}:
 * Consulo registers {@link ExtensionImpl} extensions on top-level classes only.
 */
@ExtensionImpl
public final class CaptureAsyncStackTraceProvider implements AsyncStackTraceProvider {
    @Override
    public @Nullable List<StackFrameItem> getAsyncStackTrace(JavaStackFrame stackFrame, SuspendContextImpl suspendContext) {
        return StackCapturingLineBreakpoint.getRelatedStack(stackFrame.getStackFrameProxy(), suspendContext);
    }
}
