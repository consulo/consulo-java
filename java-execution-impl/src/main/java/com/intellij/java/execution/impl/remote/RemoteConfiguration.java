/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.remote;

import com.intellij.java.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.java.debugger.impl.engine.RemoteStateState;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.compiler.execution.CompileStepBeforeRun;
import consulo.execution.configuration.*;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.debugger.impl.GenericDebugRunnerConfiguration;
import consulo.module.Module;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.project.Project;

import java.util.Collection;
import java.util.List;

/*
 * @author Jeka
 */
public class RemoteConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements
		RunConfigurationWithSuppressedDefaultRunAction, CompileStepBeforeRun.Suppressor, GenericDebugRunnerConfiguration, RemoteRunProfile
{
	public RemoteConfiguration(final Project project, ConfigurationFactory configurationFactory)
	{
		super(new JavaRunConfigurationModule(project, true), configurationFactory);
	}

	@Override
	protected Class<RemoteConfigurationOptions> getOptionsClass()
	{
		return RemoteConfigurationOptions.class;
	}

	@Override
	public RemoteConfigurationOptions getOptions()
	{
		return (RemoteConfigurationOptions) super.getOptions();
	}

	@Override
	protected void convertLegacyState(LegacyRunConfigurationConverter converter)
	{
		super.convertLegacyState(converter);

		RemoteConfigurationLegacyState old = converter.readXml(RemoteConfigurationLegacyState.class);

		RemoteConfigurationOptions options = new RemoteConfigurationOptions();
		options.setUseSocketTransport(old.USE_SOCKET_TRANSPORT || !Platform.current().os().isWindows());
		options.setServerMode(old.SERVER_MODE);
		options.setShmemAddress(old.SHMEM_ADDRESS);
		options.setHost(old.HOST);
		options.setPort(old.PORT);
		converter.setOptions(options);
	}

	@Override
	public List<RunConfigurationFragment<?>> createFragments()
	{
		return List.of(new RemoteConnectionFragment());
	}

	public RemoteConnection createRemoteConnection()
	{
		RemoteConfigurationOptions options = getOptions();
		boolean useSockets = options.isUseSocketTransport();
		return new RemoteConnection(useSockets, options.getHost(), useSockets ? options.getPort() : options.getShmemAddress(), options.isServerMode());
	}

	@Override
	public RunProfileState getState(final Executor executor, final ExecutionEnvironment env) throws ExecutionException
	{
		RemoteConfigurationOptions options = getOptions();
		GenericDebuggerRunnerSettings debuggerSettings = (GenericDebuggerRunnerSettings) env.getRunnerSettings();
		debuggerSettings.LOCAL = false;
		debuggerSettings.setDebugPort(options.isUseSocketTransport() ? options.getPort() : options.getShmemAddress());
		debuggerSettings.setTransport(options.isUseSocketTransport() ? DebuggerSettings.SOCKET_TRANSPORT : DebuggerSettings.SHMEM_TRANSPORT);
		return new RemoteStateState(getProject(), createRemoteConnection());
	}

	@Override
	public Collection<Module> getValidModules()
	{
		return getAllModules();
	}
}
