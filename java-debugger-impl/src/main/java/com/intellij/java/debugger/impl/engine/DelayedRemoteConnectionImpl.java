// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.execution.configurations.RemoteConnection;
import org.jspecify.annotations.Nullable;

public class DelayedRemoteConnectionImpl extends RemoteConnection implements DelayedRemoteConnection {
    private @Nullable Runnable myAttachRunnable;

    public DelayedRemoteConnectionImpl(boolean useSockets, String hostName, String address, boolean serverMode) {
        super(useSockets, hostName, address, serverMode);
    }

    @Nullable
    @Override
    public Runnable getAttachRunnable() {
        return myAttachRunnable;
    }

    @Override
    public void setAttachRunnable(@Nullable Runnable attachRunnable) {
        myAttachRunnable = attachRunnable;
    }
}
