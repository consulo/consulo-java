/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.impl.ui.breakpoints.BreakpointManager;
import consulo.process.ExecutionException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

public abstract class DebuggerManagerEx extends DebuggerManager {
    public static DebuggerManagerEx getInstanceEx(Project project) {
        return (DebuggerManagerEx) DebuggerManager.getInstance(project);
    }

    public abstract BreakpointManager getBreakpointManager();

    public abstract Collection<DebuggerSession> getSessions();

    public abstract DebuggerSession getSession(DebugProcess debugProcess);

    public abstract DebuggerContextImpl getContext();

    public abstract DebuggerStateManager getContextManager();

    @Nullable
    public abstract DebuggerSession attachVirtualMachine(@Nonnull DebugEnvironment environment) throws ExecutionException;
}
