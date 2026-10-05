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

import com.intellij.java.execution.configurations.JavaCommandLine;
import com.intellij.java.execution.impl.DefaultJavaProgramRunner;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.execution.configuration.ModuleRunProfile;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.RunConfigurationWithSuppressedDefaultRunAction;
import consulo.execution.executor.Executor;
import consulo.execution.executor.ExecutorGroup;
import consulo.execution.executor.ExecutorRegistry;
import consulo.execution.profiler.DefaultProfilerExecutorGroup;
import consulo.execution.profiler.ProfilerConfigurationExtension;
import consulo.execution.profiler.ProfilerExecutorSettings;
import consulo.execution.profiler.ProfilerProcess;
import consulo.execution.profiler.ProfilerToolWindowManager;
import consulo.execution.profiler.SimpleProfilerLaunchContext;
import consulo.execution.profiler.configuration.ProfilerConfigurationState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.RunContentDescriptor;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs Java run configurations under a "Profile with" executor: the {@link ProfilerConfigurationExtension}s of the
 * chosen profiler configuration patch the JVM options, and the profiled process opens as a profiling session.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
@ExtensionImpl
public class JavaProfilerProgramRunner extends DefaultJavaProgramRunner {
    private static final Logger LOG = Logger.getInstance(JavaProfilerProgramRunner.class);

    private final Application myApplication;
    private final ExecutorRegistry myExecutorRegistry;

    @Inject
    public JavaProfilerProgramRunner(Application application, ExecutorRegistry executorRegistry) {
        myApplication = application;
        myExecutorRegistry = executorRegistry;
    }

    @Override
    public String getRunnerId() {
        return "JavaProfiler";
    }

    @Override
    public boolean canRun(String executorId, RunProfile profile) {
        if (!(profile instanceof ModuleRunProfile) || profile instanceof RunConfigurationWithSuppressedDefaultRunAction) {
            return false;
        }
        Executor executor = myExecutorRegistry.getExecutorById(executorId);
        ProfilerExecutorSettings settings = executor == null ? null : findSettings(executor);
        return settings != null && settings.canRun(profile)
            && profile instanceof RunConfigurationBase configuration
            && !findExtensions(configuration, settings.getState()).isEmpty();
    }

    @Override
    protected @Nullable RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment environment) throws ExecutionException {
        ProfilerExecutorSettings settings = findSettings(environment.getExecutor());
        if (settings == null || !(environment.getRunProfile() instanceof RunConfigurationBase configuration)) {
            return super.doExecute(state, environment);
        }

        ProfilerConfigurationState profilerState = settings.getState();
        List<ProfilerConfigurationExtension> extensions = findExtensions(configuration, profilerState);
        SimpleProfilerLaunchContext context = new SimpleProfilerLaunchContext(
            environment.getProject(),
            environment.getExecutor(),
            environment.getRunnerSettings()
        );

        if (state instanceof JavaCommandLine javaCommandLine) {
            OwnJavaParameters parameters = javaCommandLine.getJavaParameters();
            List<String> vmOptions = new ArrayList<>();
            context.withVmOptions(vmOptions).withEnvironment(parameters.getEnv()).withLaunchParameters(parameters);
            for (ProfilerConfigurationExtension extension : extensions) {
                extension.patch(configuration, profilerState, context);
            }
            for (String vmOption : vmOptions) {
                parameters.getVMParametersList().add(vmOption);
            }
        }

        RunContentDescriptor descriptor = super.doExecute(state, environment);
        ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();
        if (processHandler == null) {
            return descriptor;
        }

        for (ProfilerConfigurationExtension extension : extensions) {
            ProfilerProcess<?> process;
            try {
                process = extension.attachToProcess(configuration, processHandler, profilerState, context);
            }
            catch (RuntimeException e) {
                LOG.error("Profiler extension " + extension + " failed to attach to " + configuration.getName(), e);
                continue;
            }
            if (process != null) {
                ProfilerToolWindowManager.getInstance(environment.getProject()).addProfilerProcessTab(process, true);
                break;
            }
        }
        return descriptor;
    }

    private static @Nullable ProfilerExecutorSettings findSettings(Executor executor) {
        if (ExecutorGroup.getGroupIfProxy(executor) instanceof DefaultProfilerExecutorGroup profilerGroup) {
            return profilerGroup.getRegisteredSettings(executor.getId());
        }
        return null;
    }

    private List<ProfilerConfigurationExtension> findExtensions(RunConfigurationBase configuration, ProfilerConfigurationState state) {
        List<ProfilerConfigurationExtension> extensions = new ArrayList<>();
        myApplication.getExtensionPoint(ProfilerConfigurationExtension.class).forEach(extension -> {
            if (extension.isApplicableFor(configuration) && extension.isEnabledFor(configuration, state)) {
                extensions.add(extension);
            }
        });
        return extensions;
    }
}
