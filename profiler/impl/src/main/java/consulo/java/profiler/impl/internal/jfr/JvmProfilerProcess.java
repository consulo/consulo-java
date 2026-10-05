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
package consulo.java.profiler.impl.internal.jfr;

import consulo.java.profiler.localize.JavaProfilerLocalize;
import consulo.java.profiler.impl.internal.jvm.JvmConnection;
import consulo.java.profiler.impl.internal.jvm.JvmProfilerMonitor;
import consulo.java.profiler.impl.internal.jvm.JvmRecordingFiles;
import consulo.java.profiler.impl.internal.jvm.JvmTargetProcess;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.disposer.Disposable;
import consulo.execution.profiler.DataReady;
import consulo.execution.profiler.NewCallTreeOnlyProfilerData;
import consulo.execution.profiler.ProfilerData;
import consulo.execution.profiler.ProfilerProcess;
import consulo.execution.profiler.ProfilerState;
import consulo.execution.profiler.ProfilingFailed;
import consulo.execution.profiler.configuration.ProfilerFeature;
import consulo.execution.profiler.live.LiveProfilerProcess;
import consulo.execution.profiler.live.ProfilerMonitorSink;
import consulo.java.profiler.impl.internal.jfr.snapshot.JavaCallStackElementRenderer;
import consulo.java.profiler.impl.internal.jfr.snapshot.JfrDumpWriter;
import consulo.java.profiler.impl.internal.jfr.snapshot.JfrStackReader;
import consulo.java.profiler.jfr.JfrRecorder;
import consulo.java.profiler.jfr.JfrRecording;
import consulo.logging.Logger;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A JVM profiled through JMX and JFR. A launched JVM records the whole run with {@code -XX:StartFlightRecording}; an
 * attached JVM records from the attach on. Either way the session ends with that recording, and CPU recordings in
 * between are separate JFR recordings.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JvmProfilerProcess extends ProfilerProcess<JvmTargetProcess> implements LiveProfilerProcess {
    public static final Set<ProfilerFeature> FEATURES = Set.of(ProfilerFeature.LIVE_MONITORING, ProfilerFeature.CPU_RECORDING);

    private static final Logger LOG = Logger.getInstance(JvmProfilerProcess.class);

    private static final long CONNECT_RETRY_MS = 500;
    private static final long CONNECT_TIMEOUT_MS = 30_000;
    private static final long EXIT_WAIT_MS = 10_000;

    private final ApplicationConcurrency myConcurrency;
    private final JfrProfilerConfigurationState myConfiguration;
    private final @Nullable ProcessHandler myProcessHandler;
    private final Path mySessionRecordingFile;
    private final long myAttachedTimestamp = System.currentTimeMillis();
    private final CompletableFuture<JvmConnection> myConnection = new CompletableFuture<>();
    private final List<JvmProfilerMonitor> myMonitors = new CopyOnWriteArrayList<>();
    private final AtomicBoolean myFinished = new AtomicBoolean();
    private final AtomicReference<@Nullable JfrRecording> myCapture = new AtomicReference<>();
    private volatile @Nullable JfrRecording mySessionRecording;
    private volatile @Nullable Path myStoppedRecordingFile;

    private JvmProfilerProcess(
        Project project,
        ApplicationConcurrency concurrency,
        JvmTargetProcess targetProcess,
        JfrProfilerConfigurationState configuration,
        @Nullable ProcessHandler processHandler,
        Path sessionRecordingFile
    ) {
        super(project, targetProcess);
        myConcurrency = concurrency;
        myConfiguration = configuration;
        myProcessHandler = processHandler;
        mySessionRecordingFile = sessionRecordingFile;
    }

    /**
     * Profiles a JVM which is already connected; the session records from now on.
     */
    public static JvmProfilerProcess attached(
        Project project,
        ApplicationConcurrency concurrency,
        JvmTargetProcess targetProcess,
        JfrProfilerConfigurationState configuration,
        JvmConnection connection
    ) throws IOException {
        JvmProfilerProcess process = new JvmProfilerProcess(
            project,
            concurrency,
            targetProcess,
            configuration,
            null,
            JvmRecordingFiles.create("session")
        );
        process.myConnection.complete(connection);
        process.startSessionRecording(connection);
        return process;
    }

    /**
     * Profiles a JVM launched with {@code -XX:StartFlightRecording} writing into the recording file on exit.
     */
    public static JvmProfilerProcess launched(
        Project project,
        ApplicationConcurrency concurrency,
        JvmTargetProcess targetProcess,
        JfrProfilerConfigurationState configuration,
        ProcessHandler processHandler,
        Path launchRecordingFile
    ) {
        JvmProfilerProcess process = new JvmProfilerProcess(
            project,
            concurrency,
            targetProcess,
            configuration,
            processHandler,
            launchRecordingFile
        );
        processHandler.addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(ProcessEvent event) {
                process.finish(true);
            }
        });
        if (processHandler.isProcessTerminated()) {
            process.finish(true);
        }
        else {
            process.connect(System.currentTimeMillis() + CONNECT_TIMEOUT_MS);
        }
        return process;
    }

    @Override
    public long getAttachedTimestamp() {
        return myAttachedTimestamp;
    }

    @Override
    public JfrProfilerConfigurationState getProfilerConfiguration() {
        return myConfiguration;
    }

    @Override
    public boolean canStop() {
        return !myFinished.get();
    }

    @Override
    public void stop() {
        ProcessHandler processHandler = myProcessHandler;
        if (processHandler != null && !processHandler.isProcessTerminated()) {
            if (!myFinished.get() && !processHandler.isProcessTerminating()) {
                myConcurrency.executor().execute(() -> {
                    copyLaunchRecording();
                    finish(true);
                    processHandler.destroyProcess();
                });
            }
            return;
        }
        finish(processHandler != null);
    }

    @Override
    public Set<ProfilerFeature> getFeatures() {
        return FEATURES;
    }

    @Override
    public Disposable startMonitoring(ProfilerMonitorSink sink) {
        if (myFinished.get()) {
            return () -> {
            };
        }

        JvmProfilerMonitor monitor = new JvmProfilerMonitor(sink, myMonitors::remove, this::connectionLost);
        myMonitors.add(monitor);
        myConnection.thenAccept(connection -> monitor.start(connection, myConcurrency.getScheduledExecutorService()));
        if (myFinished.get()) {
            myConcurrency.executor().execute(() -> monitor.terminate(Instant.now()));
        }
        return monitor;
    }

    @Override
    public CompletableFuture<?> startCpuRecording() {
        if (myFinished.get()) {
            return CompletableFuture.failedFuture(ended());
        }

        return myConnection.thenAcceptAsync(connection -> {
            JfrRecording recording;
            try {
                recording = startRecording(requireFlightRecorder(connection), connection, null);
            }
            catch (IOException e) {
                throw new CompletionException(e);
            }
            JfrRecording previous = myCapture.getAndSet(recording);
            if (previous != null) {
                previous.close();
            }
        }, myConcurrency.executor());
    }

    @Override
    public CompletableFuture<ProfilerData> stopCpuRecording() {
        JfrRecording recording = myCapture.getAndSet(null);
        if (recording == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No CPU recording is running"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path file = JvmRecordingFiles.create("capture");
                recording.stopAndCopy(file);
                return read(file);
            }
            catch (IOException e) {
                throw new CompletionException(e);
            }
        }, myConcurrency.executor());
    }

    private void startSessionRecording(JvmConnection connection) {
        JfrRecorder recorder = connection.getFlightRecorder();
        if (recorder == null) {
            return;
        }
        try {
            mySessionRecording = startRecording(recorder, connection, mySessionRecordingFile);
        }
        catch (IOException | RuntimeException e) {
            LOG.warn("Can't start the session recording of " + getTargetProcess().getFullName(), e);
        }
    }

    private void connect(long deadline) {
        if (myFinished.get()) {
            myConnection.completeExceptionally(ended());
            return;
        }

        try {
            myConnection.complete(JvmConnection.connect(getTargetProcess().getPid()));
        }
        catch (IOException | RuntimeException e) {
            if (System.currentTimeMillis() > deadline) {
                LOG.warn("Can't connect to " + getTargetProcess().getFullName(), e);
                myConnection.completeExceptionally(e);
                return;
            }
            myConcurrency.getScheduledExecutorService().schedule(() -> connect(deadline), CONNECT_RETRY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void connectionLost() {
        if (myProcessHandler == null) {
            finish(true);
            return;
        }

        Instant now = Instant.now();
        for (JvmProfilerMonitor monitor : myMonitors) {
            monitor.terminate(now);
        }
    }

    private void finish(boolean targetEnded) {
        if (!myFinished.compareAndSet(false, true)) {
            return;
        }

        Instant finished = Instant.now();
        myConcurrency.executor().execute(() -> {
            for (JvmProfilerMonitor monitor : myMonitors) {
                if (targetEnded) {
                    monitor.terminate(finished);
                }
                else {
                    monitor.detach();
                }
            }
            myMonitors.clear();

            JfrRecording capture = myCapture.getAndSet(null);
            if (capture != null) {
                capture.close();
            }

            collectSessionRecording();

            JvmConnection connection = connectionNow();
            if (connection != null) {
                connection.close();
            }
            else {
                myConnection.completeExceptionally(ended());
            }

            if (getProject().isDisposed()) {
                return;
            }
            setState(readSessionRecording());
        });
    }

    /**
     * Copies the whole-run recording of a launched JVM before it is stopped, so the result does not depend on the JVM
     * writing its dump on exit, which a killed JVM never does.
     */
    private void copyLaunchRecording() {
        JvmConnection connection = connectionNow();
        JfrRecorder recorder = connection == null ? null : connection.getFlightRecorder();
        if (connection == null || recorder == null) {
            return;
        }
        try {
            Path file = JvmRecordingFiles.create("launch");
            if (recorder.copyRunning(JfrOptions.recordingName(), file)) {
                myStoppedRecordingFile = file;
            }
        }
        catch (IOException | RuntimeException e) {
            LOG.warn("Can't copy the recording of " + getTargetProcess().getFullName() + "; reading the one it writes on exit", e);
        }
    }

    private @Nullable JvmConnection connectionNow() {
        try {
            return myConnection.getNow(null);
        }
        catch (CompletionException | CancellationException e) {
            return null;
        }
    }

    private void collectSessionRecording() {
        JfrRecording sessionRecording = mySessionRecording;
        mySessionRecording = null;
        if (sessionRecording == null) {
            return;
        }

        try {
            sessionRecording.stopAndCopy(mySessionRecordingFile);
        }
        catch (IOException | RuntimeException e) {
            LOG.debug("The JVM ended before its session recording was stopped; reading the dump it wrote on exit", e);
            waitForExit();
        }
    }

    private void waitForExit() {
        ProcessHandle.of(getTargetProcess().getPid()).ifPresent(handle -> {
            try {
                handle.onExit().get(EXIT_WAIT_MS, TimeUnit.MILLISECONDS);
            }
            catch (Exception e) {
                LOG.debug("The JVM " + getTargetProcess().getPid() + " did not exit in time", e);
            }
        });
    }

    private ProfilerState readSessionRecording() {
        Path stoppedRecordingFile = myStoppedRecordingFile;
        Path file = stoppedRecordingFile == null ? mySessionRecordingFile : stoppedRecordingFile;
        try {
            if (!Files.isRegularFile(file) || Files.size(file) == 0) {
                return new ProfilingFailed(JavaProfilerLocalize.jfrErrorNoData());
            }
            return new DataReady(read(file), new JfrDumpWriter(file, JvmRecordingFiles.dumpFileName(getTargetProcess())));
        }
        catch (IOException | RuntimeException e) {
            LOG.warn("Can't read the recording of " + getTargetProcess().getFullName() + " from " + file, e);
            return new ProfilingFailed(JavaProfilerLocalize.jfrErrorRead(String.valueOf(e.getMessage())));
        }
    }

    private ProfilerData read(Path file) throws IOException {
        return new NewCallTreeOnlyProfilerData(
            JfrStackReader.read(file, myConfiguration.isNativeSamples(), null),
            JavaCallStackElementRenderer.INSTANCE
        );
    }

    private JfrRecording startRecording(JfrRecorder recorder, JvmConnection connection, @Nullable Path dumpOnExit) throws IOException {
        String preset = myConfiguration.getPreset().getJfcName();
        Map<String, String> settings = JfrOptions.eventSettings(myConfiguration, connection.getFeatureVersion(), connection.isLinux());
        try {
            return recorder.start(JfrOptions.recordingName(), preset, settings, dumpOnExit);
        }
        catch (IOException e) {
            if (settings.isEmpty()) {
                throw e;
            }
            LOG.warn("Can't apply the sampling settings to " + getTargetProcess().getFullName() + "; recording with '" + preset + "' only", e);
            return recorder.start(JfrOptions.recordingName(), preset, Map.of(), dumpOnExit);
        }
    }

    private static JfrRecorder requireFlightRecorder(JvmConnection connection) {
        JfrRecorder recorder = connection.getFlightRecorder();
        if (recorder == null) {
            throw new IllegalStateException("The JVM has no Flight Recorder");
        }
        return recorder;
    }

    private IllegalStateException ended() {
        return new IllegalStateException("Profiling of " + getTargetProcess().getFullName() + " has already ended");
    }
}
