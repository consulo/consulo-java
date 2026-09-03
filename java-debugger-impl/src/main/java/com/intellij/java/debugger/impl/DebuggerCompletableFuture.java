// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This implementation of {@link CompletableFuture} ensures that it cannot be awaited
 * on {@link com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl}, as it
 * will lead to deadlock.
 */
final class DebuggerCompletableFuture<T> extends CompletableFuture<T> {
    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new DebuggerCompletableFuture<>();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        assertNotDebuggerThreadOrCompleted();
        return super.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        assertNotDebuggerThreadOrCompleted();
        return super.get(timeout, unit);
    }

    @Override
    public T join() {
        assertNotDebuggerThreadOrCompleted();
        return super.join();
    }

    private void assertNotDebuggerThreadOrCompleted() {
        if (isDone()) {
            return;
        }
        if (DebuggerManagerThreadImpl.isManagerThread()) {
            throw new IllegalStateException("Should not be called from the debugger thread");
        }
    }
}
