/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.debugger.RunConfigurationWithRunnerSettings;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JavaDebugProcess;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.ui.tree.render.BatchEvaluator;
import com.intellij.java.execution.configurations.*;
import com.intellij.java.execution.runners.JavaPatchableProgramRunner;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.document.FileDocumentManager;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.*;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.RunContentDescriptor;
import consulo.java.debugger.impl.GenericDebugRunnerConfiguration;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.process.ExecutionException;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl(id = "defaultJavaDebugRunner")
public class GenericDebuggerRunner extends JavaPatchableProgramRunner<GenericDebuggerRunnerSettings> {
    @Override
    public boolean canRun(@Nonnull String executorId, @Nonnull RunProfile profile) {
        return executorId.equals(DefaultDebugExecutor.EXECUTOR_ID) && profile instanceof GenericDebugRunnerConfiguration;
    }

    @Override
    @Nonnull
    public String getRunnerId() {
        return DebuggingRunnerData.DEBUGGER_RUNNER_ID;
    }

    @Override
    protected RunContentDescriptor doExecute(@Nonnull RunProfileState state, @Nonnull ExecutionEnvironment env) throws ExecutionException {
        FileDocumentManager.getInstance().saveAllDocuments();
        return createContentDescriptor(state, env);
    }

    @Nullable
    protected RunContentDescriptor createContentDescriptor(
        @Nonnull RunProfileState state,
        @Nonnull ExecutionEnvironment environment
    ) throws ExecutionException {
        if (state instanceof JavaCommandLine commandLine) {
            OwnJavaParameters parameters = commandLine.getJavaParameters();
            runCustomPatchers(parameters, environment.getExecutor(), environment.getRunProfile());
            RemoteConnection connection = DebuggerManagerImpl.createDebugParameters(
                parameters,
                true,
                DebuggerSettings.getInstance().DEBUGGER_TRANSPORT,
                "",
                false
            );
            return attachVirtualMachine(state, environment, connection, true);
        }
        if (state instanceof PatchedRunnableState) {
            RemoteConnection connection = doPatch(new OwnJavaParameters(), environment.getRunnerSettings());
            return attachVirtualMachine(state, environment, connection, true);
        }
        if (state instanceof RemoteState remoteState) {
            RemoteConnection connection = createRemoteDebugConnection(remoteState, environment.getRunnerSettings());
            return attachVirtualMachine(state, environment, connection, false);
        }

        return null;
    }

    @Nullable
    protected RunContentDescriptor attachVirtualMachine(
        RunProfileState state,
        @Nonnull ExecutionEnvironment env,
        RemoteConnection connection,
        boolean pollConnection
    ) throws ExecutionException {
        DebugEnvironment environment = new DefaultDebugEnvironment(env, state, connection, pollConnection);
        DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(env.getProject()).attachVirtualMachine(environment);
        if (debuggerSession == null) {
            return null;
        }

        DebugProcessImpl debugProcess = debuggerSession.getProcess();
        if (debugProcess.isDetached() || debugProcess.isDetaching()) {
            debuggerSession.dispose();
            return null;
        }
        if (environment.isRemote()) {
            // optimization: that way BatchEvaluator will not try to lookup the class file in remote VM
            // which is an expensive operation when executed first time
            debugProcess.putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
        }

        return XDebuggerManager.getInstance(env.getProject()).startSession(env, session -> {
            ExecutionResult executionResult = debugProcess.getExecutionResult();
            session.addExtraActions(executionResult.getActions());
            if (executionResult instanceof DefaultExecutionResult) {
                session.addRestartActions(((DefaultExecutionResult)executionResult).getRestartActions());
            }
            return JavaDebugProcess.create(session, debuggerSession);
        }).getRunContentDescriptor();
    }

    private static RemoteConnection createRemoteDebugConnection(RemoteState connection, RunnerSettings settings) {
        RemoteConnection remoteConnection = connection.getRemoteConnection();

        GenericDebuggerRunnerSettings debuggerRunnerSettings = (GenericDebuggerRunnerSettings)settings;

        if (debuggerRunnerSettings != null) {
            remoteConnection.setUseSockets(debuggerRunnerSettings.getTransport() == DebuggerSettings.SOCKET_TRANSPORT);
            remoteConnection.setAddress(debuggerRunnerSettings.getDebugPort());
        }

        return remoteConnection;
    }

    @Override
    public GenericDebuggerRunnerSettings createConfigurationData(ConfigurationInfoProvider settingsProvider) {
        return new GenericDebuggerRunnerSettings();
    }

    @Override
    public void patch(
        OwnJavaParameters javaParameters,
        RunnerSettings settings,
        RunProfile runProfile,
        boolean beforeExecution
    ) throws ExecutionException {
        doPatch(javaParameters, settings);
        runCustomPatchers(
            javaParameters,
            Application.get().getExtensionPoint(Executor.class).findExtension(DefaultDebugExecutor.class),
            runProfile
        );
    }

    private static RemoteConnection doPatch(OwnJavaParameters javaParameters, RunnerSettings settings) throws ExecutionException {
        GenericDebuggerRunnerSettings debuggerSettings = ((GenericDebuggerRunnerSettings)settings);
        if (StringUtil.isEmpty(debuggerSettings.getDebugPort())) {
            debuggerSettings.setDebugPort(DebuggerUtils.getInstance().findAvailableDebugAddress(debuggerSettings.getTransport()).address());
        }
        return DebuggerManagerImpl.createDebugParameters(javaParameters, debuggerSettings, false);
    }

    @Override
    public SettingsEditor<GenericDebuggerRunnerSettings> getSettingsEditor(Executor executor, RunConfiguration configuration) {
        if (configuration instanceof RunConfigurationWithRunnerSettings runConfigurationWithRunnerSettings
            && runConfigurationWithRunnerSettings.isSettingsNeeded()) {
            return new GenericDebuggerParametersRunnerConfigurable(configuration.getProject());
        }
        return null;
    }
}
