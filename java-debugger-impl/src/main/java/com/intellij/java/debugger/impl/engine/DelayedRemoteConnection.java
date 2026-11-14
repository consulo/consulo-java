// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.engine;

import org.jspecify.annotations.Nullable;

/**
 * Provide an instance of this class to RemoteState in order to perform deferred attaching to the virtual machine:
 * {@link DebugProcessImpl#attachVirtualMachine} does not connect, it sets the attach runnable instead.
 */
public interface DelayedRemoteConnection {
    /**
     * Must be invoked on EDT.
     */
    @Nullable
    Runnable getAttachRunnable();

    void setAttachRunnable(@Nullable Runnable attachRunnable);
}
