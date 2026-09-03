// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.engine;

import consulo.logging.Logger;
import consulo.util.dataholder.Key;

import java.util.concurrent.TimeUnit;

public final class AsyncStackTracesOverheadUtils {
    private static final Logger LOG = Logger.getInstance(AsyncStackTracesOverheadUtils.class);
    private static final Key<Long> SESSION_START_TIMESTAMP_KEY = Key.create("debuggerSessionStartTimestamp");

    private AsyncStackTracesOverheadUtils() {
    }

    static void initializeOverheadListener(DebugProcessImpl process) {
        long startNs = System.nanoTime();
        process.putUserData(SESSION_START_TIMESTAMP_KEY, startNs);
    }

    static void onOverheadDetected(DebugProcessImpl process) {
        Long sessionStartNs = process.getUserData(SESSION_START_TIMESTAMP_KEY);
        long sessionLengthMs;
        if (sessionStartNs != null) {
            long durationNs = System.nanoTime() - sessionStartNs;
            sessionLengthMs = TimeUnit.NANOSECONDS.toMillis(durationNs);
        }
        else {
            sessionLengthMs = -1;
        }
        LOG.info("Debugger agent overhead detected, session length " + sessionLengthMs + " ms");
    }
}
