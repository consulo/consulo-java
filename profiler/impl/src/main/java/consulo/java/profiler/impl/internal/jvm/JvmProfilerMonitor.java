/*
 * Copyright 2013-2026 consulo.io
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
package consulo.java.profiler.impl.internal.jvm;

import consulo.java.profiler.localize.JavaProfilerLocalize;
import consulo.disposer.Disposable;
import consulo.execution.profiler.live.ProfilerMetric;
import consulo.execution.profiler.live.ProfilerMonitorSink;
import consulo.execution.profiler.live.ProfilerThreadState;
import consulo.logging.Logger;
import consulo.ui.chart.ChartUnit;
import consulo.ui.chart.TimeSeriesKind;
import org.jspecify.annotations.Nullable;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Polls the JMX beans of a JVM and pushes CPU, GC, memory and thread values into a sink.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JvmProfilerMonitor implements Disposable, Runnable {
    static final ProfilerMetric PROCESS_CPU = new ProfilerMetric(
        "java.cpu.process",
        JavaProfilerLocalize.monitorProcessCpu(),
        ChartUnit.PERCENT,
        "cpu",
        TimeSeriesKind.AREA
    );
    static final ProfilerMetric SYSTEM_CPU = new ProfilerMetric(
        "java.cpu.system",
        JavaProfilerLocalize.monitorSystemCpu(),
        ChartUnit.PERCENT,
        "cpu",
        TimeSeriesKind.LINE
    );
    static final ProfilerMetric GC_ACTIVITY = new ProfilerMetric(
        "java.cpu.gc",
        JavaProfilerLocalize.monitorGcActivity(),
        ChartUnit.PERCENT,
        "cpu",
        TimeSeriesKind.LINE
    );
    static final ProfilerMetric HEAP_USED = new ProfilerMetric(
        "java.memory.heap.used",
        JavaProfilerLocalize.monitorHeapUsed(),
        ChartUnit.BYTES,
        "memory",
        TimeSeriesKind.AREA
    );
    static final ProfilerMetric HEAP_COMMITTED = new ProfilerMetric(
        "java.memory.heap.committed",
        JavaProfilerLocalize.monitorHeapCommitted(),
        ChartUnit.BYTES,
        "memory",
        TimeSeriesKind.LINE
    );
    static final ProfilerMetric NON_HEAP_USED = new ProfilerMetric(
        "java.memory.nonheap.used",
        JavaProfilerLocalize.monitorNonHeapUsed(),
        ChartUnit.BYTES,
        "memory",
        TimeSeriesKind.LINE
    );
    static final ProfilerMetric LIVE_THREADS = new ProfilerMetric(
        "java.threads.live",
        JavaProfilerLocalize.monitorThreadsLive(),
        ChartUnit.COUNT,
        "threads",
        TimeSeriesKind.LINE
    );
    static final ProfilerMetric DAEMON_THREADS = new ProfilerMetric(
        "java.threads.daemon",
        JavaProfilerLocalize.monitorThreadsDaemon(),
        ChartUnit.COUNT,
        "threads",
        TimeSeriesKind.LINE
    );

    private static final Logger LOG = Logger.getInstance(JvmProfilerMonitor.class);

    private static final long PERIOD_MS = 1000;

    private final ProfilerMonitorSink mySink;
    private final Consumer<JvmProfilerMonitor> myOnDispose;
    private final Runnable myOnConnectionLost;
    private final Map<Long, ThreadEntry> myThreads = new HashMap<>();
    private final Set<String> myFailedProbes = new HashSet<>();

    private @Nullable JvmConnection myConnection;
    private @Nullable ScheduledFuture<?> myFuture;
    private boolean myStopped;
    private long myLastGcTime = -1;
    private long myLastTickNanos;

    public JvmProfilerMonitor(ProfilerMonitorSink sink, Consumer<JvmProfilerMonitor> onDispose, Runnable onConnectionLost) {
        mySink = sink;
        myOnDispose = onDispose;
        myOnConnectionLost = onConnectionLost;
    }

    public synchronized void start(JvmConnection connection, ScheduledExecutorService scheduler) {
        if (myStopped) {
            return;
        }
        myConnection = connection;
        myFuture = scheduler.scheduleWithFixedDelay(this, 0, PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops polling and ends the timeline of every thread still alive.
     */
    public synchronized void terminate(Instant time) {
        stop();
        for (Map.Entry<Long, ThreadEntry> entry : myThreads.entrySet()) {
            ThreadEntry thread = entry.getValue();
            if (thread.state() != ProfilerThreadState.TERMINATED) {
                mySink.threadState(entry.getKey(), thread.name(), time, ProfilerThreadState.TERMINATED);
            }
        }
        myThreads.clear();
    }

    public synchronized void detach() {
        stop();
        myThreads.clear();
    }

    @Override
    public synchronized void run() {
        JvmConnection connection = myConnection;
        if (myStopped || connection == null) {
            return;
        }

        try {
            tick(connection, Instant.now());
        }
        catch (RuntimeException e) {
            if (isConnectionFailure(e)) {
                stop();
                myOnConnectionLost.run();
            }
            else {
                LOG.warn("Polling the JVM failed", e);
            }
        }
    }

    @Override
    public void dispose() {
        synchronized (this) {
            stop();
        }
        myOnDispose.accept(this);
    }

    private void stop() {
        myStopped = true;
        ScheduledFuture<?> future = myFuture;
        if (future != null) {
            future.cancel(false);
            myFuture = null;
        }
    }

    private void tick(JvmConnection connection, Instant time) {
        long now = System.nanoTime();
        long elapsedMs = myLastTickNanos == 0 ? 0 : TimeUnit.NANOSECONDS.toMillis(now - myLastTickNanos);
        myLastTickNanos = now;

        probe("cpu", () -> reportCpu(connection, time));
        probe("gc", () -> reportGc(connection, time, elapsedMs));
        probe("memory", () -> reportMemory(connection, time));
        probe("threads", () -> reportThreads(connection, time));
    }

    private void probe(String name, Probe probe) {
        try {
            probe.run();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (JMException | RuntimeException e) {
            if (isConnectionFailure(e)) {
                throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
            }
            if (myFailedProbes.add(name)) {
                LOG.warn("Can't read the " + name + " values of the JVM", e);
            }
        }
    }

    private void reportCpu(JvmConnection connection, Instant time) throws IOException, JMException {
        double process = number(connection.getAttribute(JvmConnection.OPERATING_SYSTEM, "ProcessCpuLoad"));
        if (process >= 0) {
            mySink.metric(PROCESS_CPU, time, process * 100);
        }
        double system = number(connection.getAttribute(JvmConnection.OPERATING_SYSTEM, "SystemCpuLoad"));
        if (system >= 0) {
            mySink.metric(SYSTEM_CPU, time, system * 100);
        }
    }

    private void reportGc(JvmConnection connection, Instant time, long elapsedMs) throws IOException, JMException {
        long total = 0;
        for (ObjectName collector : connection.getCollectors()) {
            long collectionTime = (long) number(connection.getAttribute(collector, "CollectionTime"));
            if (collectionTime > 0) {
                total += collectionTime;
            }
        }

        long last = myLastGcTime;
        myLastGcTime = total;
        if (last >= 0 && elapsedMs > 0) {
            mySink.metric(GC_ACTIVITY, time, Math.clamp((total - last) * 100.0 / elapsedMs, 0, 100));
        }
    }

    private void reportMemory(JvmConnection connection, Instant time) throws IOException, JMException {
        MemoryUsage heap = MemoryUsage.from((CompositeData) connection.getAttribute(JvmConnection.MEMORY, "HeapMemoryUsage"));
        mySink.metric(HEAP_USED, time, heap.getUsed());
        mySink.metric(HEAP_COMMITTED, time, heap.getCommitted());
        MemoryUsage nonHeap = MemoryUsage.from((CompositeData) connection.getAttribute(JvmConnection.MEMORY, "NonHeapMemoryUsage"));
        mySink.metric(NON_HEAP_USED, time, nonHeap.getUsed());
    }

    private void reportThreads(JvmConnection connection, Instant time) throws IOException, JMException {
        mySink.metric(LIVE_THREADS, time, number(connection.getAttribute(JvmConnection.THREADING, "ThreadCount")));
        mySink.metric(DAEMON_THREADS, time, number(connection.getAttribute(JvmConnection.THREADING, "DaemonThreadCount")));

        long[] ids = (long[]) connection.getAttribute(JvmConnection.THREADING, "AllThreadIds");
        Object infos = connection.getServer().invoke(
            JvmConnection.THREADING,
            "getThreadInfo",
            new Object[]{ids, 1},
            new String[]{long[].class.getName(), int.class.getName()}
        );

        Set<Long> seen = new HashSet<>();
        if (infos instanceof CompositeData[] data) {
            for (CompositeData item : data) {
                if (item == null) {
                    continue;
                }
                ThreadInfo info = ThreadInfo.from(item);
                ProfilerThreadState state = toState(info);
                if (state == null) {
                    continue;
                }

                long id = info.getThreadId();
                seen.add(id);
                ThreadEntry previous = myThreads.get(id);
                if (previous == null || previous.state() != state) {
                    myThreads.put(id, new ThreadEntry(info.getThreadName(), state));
                    mySink.threadState(id, info.getThreadName(), time, state);
                }
            }
        }

        Iterator<Map.Entry<Long, ThreadEntry>> iterator = myThreads.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, ThreadEntry> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                mySink.threadState(entry.getKey(), entry.getValue().name(), time, ProfilerThreadState.TERMINATED);
                iterator.remove();
            }
        }
    }

    private static double number(@Nullable Object value) {
        return value instanceof Number number ? number.doubleValue() : -1;
    }

    private static @Nullable ProfilerThreadState toState(ThreadInfo info) {
        return switch (info.getThreadState()) {
            case RUNNABLE -> info.isInNative() ? ProfilerThreadState.RUNNING_NATIVE : ProfilerThreadState.RUNNING;
            case BLOCKED -> ProfilerThreadState.BLOCKED;
            case WAITING -> ProfilerThreadState.WAITING;
            case TIMED_WAITING -> isSleeping(info) ? ProfilerThreadState.SLEEPING : ProfilerThreadState.WAITING;
            case TERMINATED -> ProfilerThreadState.TERMINATED;
            case NEW -> null;
        };
    }

    private static boolean isSleeping(ThreadInfo info) {
        StackTraceElement[] stackTrace = info.getStackTrace();
        return stackTrace.length > 0
            && Thread.class.getName().equals(stackTrace[0].getClassName())
            && stackTrace[0].getMethodName().startsWith("sleep");
    }

    static boolean isConnectionFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private record ThreadEntry(String name, ProfilerThreadState state) {
    }

    @FunctionalInterface
    private interface Probe {
        void run() throws IOException, JMException;
    }
}
