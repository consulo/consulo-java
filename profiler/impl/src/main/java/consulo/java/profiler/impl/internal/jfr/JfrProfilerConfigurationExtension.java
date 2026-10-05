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

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.language.impl.projectRoots.JavaSdkVersionUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.profiler.ProfilerConfigurationExtension;
import consulo.execution.profiler.ProfilerLaunchContext;
import consulo.execution.profiler.ProfilerProcess;
import consulo.execution.profiler.configuration.ProfilerConfigurationState;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.profiler.impl.internal.jvm.JvmRecordingFiles;
import consulo.java.profiler.impl.internal.jvm.JvmTargetProcess;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.util.dataholder.Key;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Launches Java run configurations with a whole-run Flight Recorder recording and profiles the started JVM.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
@ExtensionImpl
public class JfrProfilerConfigurationExtension implements ProfilerConfigurationExtension {
    private static final Logger LOG = Logger.getInstance(JfrProfilerConfigurationExtension.class);

    private static final Key<Path> RECORDING_FILE = Key.create("JfrProfilerConfigurationExtension.recordingFile");

    private final ApplicationConcurrency myConcurrency;

    @Inject
    public JfrProfilerConfigurationExtension(ApplicationConcurrency concurrency) {
        myConcurrency = concurrency;
    }

    @Override
    public boolean isApplicableFor(RunConfigurationBase configuration) {
        return configuration instanceof CommonJavaRunConfigurationParameters;
    }

    @Override
    public boolean isEnabledFor(RunConfigurationBase configuration, ProfilerConfigurationState state) {
        return state instanceof JfrProfilerConfigurationState;
    }

    @Override
    public void patch(RunConfigurationBase configuration, ProfilerConfigurationState state, ProfilerLaunchContext context)
        throws ExecutionException {
        if (!(state instanceof JfrProfilerConfigurationState jfrState)) {
            return;
        }

        OwnJavaParameters parameters = context.getLaunchParameters(OwnJavaParameters.class);
        List<String> vmOptions = context.getVmOptions();
        if (parameters == null || vmOptions == null) {
            return;
        }

        Path recordingFile;
        try {
            recordingFile = JvmRecordingFiles.create("launch");
        }
        catch (IOException e) {
            throw new ExecutionException("Can't create the Flight Recorder file: " + e.getMessage(), e);
        }

        JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(parameters.getJdk());
        int featureVersion = version == null ? JfrOptions.EVENT_OPTIONS_FEATURE_VERSION : version.getMaxLanguageLevel().feature();
        vmOptions.addAll(JfrOptions.launchVmOptions(jfrState, recordingFile, featureVersion, Platform.current().os().isLinux()));
        context.putUserData(RECORDING_FILE, recordingFile);
    }

    @Override
    public @Nullable ProfilerProcess<?> attachToProcess(
        RunConfigurationBase configuration,
        ProcessHandler handler,
        ProfilerConfigurationState state,
        ProfilerLaunchContext context
    ) {
        Path recordingFile = context.getUserData(RECORDING_FILE);
        if (!(state instanceof JfrProfilerConfigurationState jfrState) || recordingFile == null) {
            return null;
        }
        long id = handler.getId();
        if (id <= 0) {
            LOG.warn("Can't profile " + configuration.getName() + ": its process handler has no OS process");
            return null;
        }

        int pid = (int) id;
        return JvmProfilerProcess.launched(
            context.getProject(),
            myConcurrency,
            new JvmTargetProcess(configuration.getName() + " (" + pid + ")", pid),
            jfrState,
            handler,
            recordingFile
        );
    }
}
