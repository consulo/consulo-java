// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl;

import com.intellij.debugger.streams.trace.impl.JavaDebuggerCommandLauncher;
import com.intellij.debugger.streams.trace.impl.JavaValueInterpreter;
import com.intellij.debugger.streams.ui.impl.JavaCollectionTreeBuilder;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.stream.lib.LibrarySupportProvider;
import consulo.execution.debug.stream.trace.CollectionTreeBuilder;
import consulo.execution.debug.stream.trace.DebuggerCommandLauncher;
import consulo.execution.debug.stream.trace.XValueInterpreter;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public abstract class JvmLibrarySupportProvider implements LibrarySupportProvider {
    private static final XValueInterpreter INTERPRETER = new JavaValueInterpreter();

    @Nonnull
    @Override
    public XValueInterpreter getXValueInterpreter(@Nonnull Project project) {
        return INTERPRETER;
    }

    @Nonnull
    @Override
    public CollectionTreeBuilder getCollectionTreeBuilder(@Nonnull Project project) {
        return new JavaCollectionTreeBuilder(project);
    }

    @Nonnull
    @Override
    public DebuggerCommandLauncher getDebuggerCommandLauncher(@Nonnull XDebugSession session) {
        return new JavaDebuggerCommandLauncher(session);
    }
}
