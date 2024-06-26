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

/*
 * @author Jeka
 */
package com.intellij.java.execution.impl.remote;

import com.intellij.java.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.java.debugger.impl.engine.RemoteStateState;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.compiler.execution.CompileStepBeforeRun;
import consulo.execution.configuration.*;
import consulo.execution.configuration.log.ui.LogConfigurationPanel;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.configuration.ui.SettingsEditorGroup;
import consulo.execution.executor.Executor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.debugger.impl.GenericDebugRunnerConfiguration;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;

import java.util.Collection;

public class RemoteConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements
		RunConfigurationWithSuppressedDefaultRunAction, CompileStepBeforeRun.Suppressor, GenericDebugRunnerConfiguration, RemoteRunProfile
{

	@Override
	public void writeExternal(final Element element) throws WriteExternalException
	{
		super.writeExternal(element);
		final Module module = getConfigurationModule().getModule();
		if (module != null)
		{ // default value
			writeModule(element);
		}
		DefaultJDOMExternalizer.writeExternal(this, element);
	}

	@Override
	public void readExternal(final Element element) throws InvalidDataException
	{
		super.readExternal(element);
		readModule(element);
		DefaultJDOMExternalizer.readExternal(this, element);
	}

	public boolean USE_SOCKET_TRANSPORT;
	public boolean SERVER_MODE;
	public String SHMEM_ADDRESS;
	public String HOST;
	public String PORT;

	public RemoteConfiguration(final Project project, ConfigurationFactory configurationFactory)
	{
		super(new JavaRunConfigurationModule(project, true), configurationFactory);
	}

	public RemoteConnection createRemoteConnection()
	{
		return new RemoteConnection(USE_SOCKET_TRANSPORT, HOST, USE_SOCKET_TRANSPORT ? PORT : SHMEM_ADDRESS, SERVER_MODE);
	}

	@Override
	public RunProfileState getState(@Nonnull final Executor executor, @Nonnull final ExecutionEnvironment env) throws ExecutionException
	{
		GenericDebuggerRunnerSettings debuggerSettings = (GenericDebuggerRunnerSettings) env.getRunnerSettings();
		debuggerSettings.LOCAL = false;
		debuggerSettings.setDebugPort(USE_SOCKET_TRANSPORT ? PORT : SHMEM_ADDRESS);
		debuggerSettings.setTransport(USE_SOCKET_TRANSPORT ? DebuggerSettings.SOCKET_TRANSPORT : DebuggerSettings.SHMEM_TRANSPORT);
		return new RemoteStateState(getProject(), createRemoteConnection());
	}

	@Override
	@Nonnull
	public SettingsEditor<? extends RunConfiguration> getConfigurationEditor()
	{
		SettingsEditorGroup<RemoteConfiguration> group = new SettingsEditorGroup<>();
		group.addEditor(ExecutionLocalize.runConfigurationConfigurationTabTitle().get(), new RemoteConfigurable(getProject()));
		group.addEditor(ExecutionLocalize.logsTabTitle().get(), new LogConfigurationPanel<>());
		return group;
	}

	@Override
	public Collection<Module> getValidModules()
	{
		return getAllModules();
	}


}
