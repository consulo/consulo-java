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

public abstract class JvmLibrarySupportProvider implements LibrarySupportProvider {
    private static final XValueInterpreter INTERPRETER = new JavaValueInterpreter();

    @Override
    public XValueInterpreter getXValueInterpreter(Project project) {
        return INTERPRETER;
    }

    @Override
    public CollectionTreeBuilder getCollectionTreeBuilder(Project project) {
        return new JavaCollectionTreeBuilder(project);
    }

    @Override
    public DebuggerCommandLauncher getDebuggerCommandLauncher(XDebugSession session) {
        return new JavaDebuggerCommandLauncher(session);
    }
}
