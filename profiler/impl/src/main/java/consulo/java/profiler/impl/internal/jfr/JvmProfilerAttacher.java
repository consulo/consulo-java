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

import consulo.java.profiler.impl.internal.jvm.JvmConnection;
import consulo.java.profiler.impl.internal.jvm.JvmDiscovery;
import consulo.java.profiler.impl.internal.jvm.JvmTargetProcess;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.execution.attach.LocalAttachHost;
import consulo.execution.attach.XAttachHost;
import consulo.execution.profiler.ProfilerProcess;
import consulo.execution.profiler.configuration.ProfilerAttacher;
import consulo.execution.profiler.configuration.ProfilerFeature;
import consulo.platform.ProcessInfo;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Attaches to local JVMs found through their perf data files. The JVM list is read once per attacher, which the profiler home
 * creates for every process scan.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public class JvmProfilerAttacher implements ProfilerAttacher {
    private final ApplicationConcurrency myConcurrency;
    private final JfrProfilerConfigurationState myState;

    private volatile @Nullable Set<Integer> myJvms;

    public JvmProfilerAttacher(ApplicationConcurrency concurrency, JfrProfilerConfigurationState state) {
        myConcurrency = concurrency;
        myState = state;
    }

    @Override
    public boolean isApplicable(XAttachHost host, ProcessInfo process) {
        return host instanceof LocalAttachHost && getJvms().contains(process.getPid());
    }

    @Override
    public Set<ProfilerFeature> getFeatures(XAttachHost host, ProcessInfo process) {
        return isApplicable(host, process) ? JvmProfilerProcess.FEATURES : Set.of();
    }

    @Override
    public CompletableFuture<ProfilerProcess<?>> attach(Project project, XAttachHost host, ProcessInfo process) {
        if (!(host instanceof LocalAttachHost)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Only local JVMs can be profiled: " + process));
        }

        int pid = process.getPid();
        return CompletableFuture.supplyAsync(() -> {
            JvmTargetProcess targetProcess = new JvmTargetProcess(displayName(process), pid);
            try {
                JvmConnection connection = JvmConnection.connect(pid);
                try {
                    return JvmProfilerProcess.attached(project, myConcurrency, targetProcess, myState, connection);
                }
                catch (IOException | RuntimeException e) {
                    connection.close();
                    throw e;
                }
            }
            catch (IOException e) {
                throw new CompletionException(e);
            }
        }, myConcurrency.executor());
    }

    private Set<Integer> getJvms() {
        Set<Integer> jvms = myJvms;
        if (jvms == null) {
            jvms = JvmDiscovery.listJvmPids();
            myJvms = jvms;
        }
        return jvms;
    }

    private static String displayName(ProcessInfo process) {
        List<String> arguments = List.of(process.getArgs().trim().split("\\s+"));
        String main = JvmDiscovery.mainOf(arguments, process.getExecutableDisplayName());
        int slash = main.lastIndexOf('/');
        return (slash >= 0 ? main.substring(slash + 1) : main) + " (" + process.getPid() + ")";
    }
}
