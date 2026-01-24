// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.engine.JavaDebugProcess;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.stream.trace.DebuggerCommandLauncher;
import jakarta.annotation.Nonnull;


public class JavaDebuggerCommandLauncher implements DebuggerCommandLauncher {
    private final XDebugSession session;

    public JavaDebuggerCommandLauncher(@Nonnull XDebugSession session) {
        this.session = session;
    }

    @Override
    public void launchDebuggerCommand(@Nonnull Runnable runnable) {
        DebuggerContextImpl debuggerContext = ((JavaDebugProcess) session.getDebugProcess())
            .getDebuggerSession()
            .getContextManager()
            .getContext();

        debuggerContext.getDebugProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                runnable.run();
            }
        });
    }
}
