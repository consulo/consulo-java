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
package com.intellij.execution;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.diagnostic.logging.OutputFileUtil;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testDiscovery.JavaAutoRunManager;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestProxyRoot;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.autotest.AbstractAutoTestManager;
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.components.PathMacroUtil;
import consulo.logging.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.Comparing;
import consulo.disposer.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.java.projectRoots.OwnJdkUtil;

public abstract class JavaTestFrameworkRunnableState<T extends ModuleBasedConfiguration<JavaRunConfigurationModule> & CommonJavaRunConfigurationParameters & ConfigurationWithCommandLineShortener &
		SMRunnerConsolePropertiesProvider> extends JavaCommandLineState implements RemoteConnectionCreator
{
	private static final Logger LOG = Logger.getInstance(JavaTestFrameworkRunnableState.class);
	protected ServerSocket myServerSocket;
	protected File myTempFile;
	protected File myWorkingDirsFile = null;

	private RemoteConnectionCreator remoteConnectionCreator;
	private final List<ArgumentFileFilter> myArgumentFileFilters = new ArrayList<>();

	public void setRemoteConnectionCreator(RemoteConnectionCreator remoteConnectionCreator)
	{
		this.remoteConnectionCreator = remoteConnectionCreator;
	}

	@Nullable
	@Override
	public RemoteConnection createRemoteConnection(ExecutionEnvironment environment)
	{
		return remoteConnectionCreator == null ? null : remoteConnectionCreator.createRemoteConnection(environment);
	}

	@Override
	public boolean isPollConnection()
	{
		return remoteConnectionCreator != null && remoteConnectionCreator.isPollConnection();
	}

	public JavaTestFrameworkRunnableState(ExecutionEnvironment environment)
	{
		super(environment);
	}

	@Nonnull
	protected abstract String getFrameworkName();

	@Nonnull
	protected abstract String getFrameworkId();

	protected abstract void passTempFile(ParametersList parametersList, String tempFilePath);

	@Nonnull
	protected abstract T getConfiguration();

	@javax.annotation.Nullable
	protected abstract TestSearchScope getScope();

	@Nonnull
	protected abstract String getForkMode();

	@Nonnull
	protected abstract OSProcessHandler createHandler(Executor executor) throws ExecutionException;

	public SearchForTestsTask createSearchingForTestsTask()
	{
		return null;
	}

	protected boolean configureByModule(Module module)
	{
		return module != null;
	}

	protected boolean isIdBasedTestTree()
	{
		return false;
	}

	@Override
	protected GeneralCommandLine createCommandLine() throws ExecutionException
	{
		GeneralCommandLine commandLine = super.createCommandLine();
		Map<String, String> content = commandLine.getUserData(OwnJdkUtil.COMMAND_LINE_CONTENT);
		if(content != null)
		{
			content.forEach((key, value) -> myArgumentFileFilters.add(new ArgumentFileFilter(key, value)));
		}
		return commandLine;
	}

	@Nonnull
	@Override
	public ExecutionResult execute(@Nonnull Executor executor, @Nonnull ProgramRunner runner) throws ExecutionException
	{
		final RunnerSettings runnerSettings = getRunnerSettings();

		final SMTRunnerConsoleProperties testConsoleProperties = getConfiguration().createTestConsoleProperties(executor);
		testConsoleProperties.setIdBasedTestTree(isIdBasedTestTree());
		testConsoleProperties.setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false);

		final BaseTestsOutputConsoleView consoleView = SMTestRunnerConnectionUtil.createConsole(getFrameworkName(), testConsoleProperties);
		final SMTestRunnerResultsForm viewer = ((SMTRunnerConsoleView) consoleView).getResultsViewer();
		Disposer.register(getConfiguration().getProject(), consoleView);

		final OSProcessHandler handler = createHandler(executor);

		for(ArgumentFileFilter filter : myArgumentFileFilters)
		{
			consoleView.addMessageFilter(filter);
		}

		consoleView.attachToProcess(handler);
		final AbstractTestProxy root = viewer.getRoot();
		if(root instanceof TestProxyRoot)
		{
			((TestProxyRoot) root).setHandler(handler);
		}
		handler.addProcessListener(new ProcessAdapter()
		{
			@Override
			public void startNotified(@Nonnull ProcessEvent event)
			{
				if(getConfiguration().isSaveOutputToFile())
				{
					final File file = OutputFileUtil.getOutputFile(getConfiguration());
					root.setOutputFilePath(file != null ? file.getAbsolutePath() : null);
				}
			}

			@Override
			public void processTerminated(@Nonnull ProcessEvent event)
			{
				Runnable runnable = () ->
				{
					root.flushOutputFile();
					deleteTempFiles();
					clear();
				};
				UIUtil.invokeLaterIfNeeded(runnable);
				handler.removeProcessListener(this);
			}
		});

		AbstractRerunFailedTestsAction rerunFailedTestsAction = testConsoleProperties.createRerunFailedTestsAction(consoleView);
		LOG.assertTrue(rerunFailedTestsAction != null);
		rerunFailedTestsAction.setModelProvider(() -> viewer);

		final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
		result.setRestartActions(rerunFailedTestsAction, new ToggleAutoTestAction()
		{
			@Override
			public boolean isDelayApplicable()
			{
				return false;
			}

			@Override
			public AbstractAutoTestManager getAutoTestManager(Project project)
			{
				return JavaAutoRunManager.getInstance(project);
			}
		});

		JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, runnerSettings);
		return result;
	}

	protected abstract void configureRTClasspath(OwnJavaParameters javaParameters) throws CantRunException;

	@Override
	protected OwnJavaParameters createJavaParameters() throws ExecutionException
	{
		final OwnJavaParameters javaParameters = new OwnJavaParameters();
		Project project = getConfiguration().getProject();
		javaParameters.setShortenCommandLine(getConfiguration().getShortenCommandLine(), project);
		final Module module = getConfiguration().getConfigurationModule().getModule();

		Sdk jdk = module == null ? null : ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
		javaParameters.setJdk(jdk);

		final String parameters = getConfiguration().getProgramParameters();
		getConfiguration().setProgramParameters(null);
		try
		{
			JavaParametersUtil.configureConfiguration(javaParameters, getConfiguration());
		}
		finally
		{
			getConfiguration().setProgramParameters(parameters);
		}
		javaParameters.getClassPath().addFirst(JavaSdkUtil.getJavaRtJarPath());
		configureClasspath(javaParameters);

		final JavaTestPatcher[] patchers = JavaTestPatcher.EP_NAME.getExtensions();
		for(JavaTestPatcher patcher : patchers)
		{
			patcher.patchJavaParameters(module, javaParameters);
		}

		// Append coverage parameters if appropriate
		for(RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME))
		{
			ext.updateJavaParameters(getConfiguration(), javaParameters, getRunnerSettings());
		}

		if(!StringUtil.isEmptyOrSpaces(parameters))
		{
			javaParameters.getProgramParametersList().addAll(getNamedParams(parameters));
		}

		if(ConsoleBuffer.useCycleBuffer())
		{
			javaParameters.getVMParametersList().addProperty("idea.test.cyclic.buffer.size", String.valueOf(ConsoleBuffer.getCycleBufferSize()));
		}

		return javaParameters;
	}

	protected List<String> getNamedParams(String parameters)
	{
		return Collections.singletonList("@name" + parameters);
	}

	private ServerSocket myForkSocket = null;

	@javax.annotation.Nullable
	public ServerSocket getForkSocket()
	{
		if(myForkSocket == null && (!Comparing.strEqual(getForkMode(), "none") || forkPerModule()) && getRunnerSettings() != null)
		{
			try
			{
				myForkSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
			}
			catch(IOException e)
			{
				LOG.error(e);
			}
		}
		return myForkSocket;
	}

	private boolean isExecutorDisabledInForkedMode()
	{
		final RunnerSettings settings = getRunnerSettings();
		return settings != null && !(settings instanceof GenericDebuggerRunnerSettings);
	}

	protected void appendForkInfo(Executor executor) throws ExecutionException
	{
		final String forkMode = getForkMode();
		if(Comparing.strEqual(forkMode, "none"))
		{
			if(forkPerModule())
			{
				if(isExecutorDisabledInForkedMode())
				{
					final String actionName = UIUtil.removeMnemonic(executor.getStartActionText());
					throw new CantRunException("'" + actionName + "' is disabled when per-module working directory is configured.<br/>" + "Please specify single working directory, or change test " +
							"scope to single module.");
				}
			}
			else
			{
				return;
			}
		}
		else if(isExecutorDisabledInForkedMode())
		{
			final String actionName = executor.getActionName();
			throw new CantRunException(actionName + " is disabled in fork mode.<br/>Please change fork mode to &lt;none&gt; to " + actionName.toLowerCase(Locale.ENGLISH) + ".");
		}

		final OwnJavaParameters javaParameters = getJavaParameters();
		final Sdk jdk = javaParameters.getJdk();
		if(jdk == null)
		{
			throw new ExecutionException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
		}

		try
		{
			final File tempFile = FileUtil.createTempFile("command.line", "", true);
			try (PrintWriter writer = new PrintWriter(tempFile, CharsetToolkit.UTF8))
			{
				if(OwnJdkUtil.useDynamicClasspath(getConfiguration().getProject()) && forkPerModule())
				{
					writer.println("use classpath jar");
				}
				else
				{
					writer.println("");
				}

				JavaSdkType sdkType = (JavaSdkType) jdk.getSdkType();
				GeneralCommandLine commandLine = new GeneralCommandLine();
				sdkType.setupCommandLine(commandLine, jdk);

				List<String> commandLineList = commandLine.getCommandLineList(null);
				for(String line : commandLineList)
				{
					writer.println(line);
				}

				for(String vmParameter : javaParameters.getVMParametersList().getList())
				{
					writer.println(vmParameter);
				}
			}

			passForkMode(getForkMode(), tempFile, javaParameters);
		}
		catch(IOException e)
		{
			LOG.error(e);
		}
	}

	protected abstract void passForkMode(String forkMode, File tempFile, OwnJavaParameters parameters) throws ExecutionException;

	protected void collectListeners(OwnJavaParameters javaParameters, StringBuilder buf, String epName, String delimiter)
	{
		final T configuration = getConfiguration();
		final Object[] listeners = Extensions.getExtensions(epName);
		for(final Object listener : listeners)
		{
			boolean enabled = true;
			for(RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME))
			{
				if(ext.isListenerDisabled(configuration, listener, getRunnerSettings()))
				{
					enabled = false;
					break;
				}
			}
			if(enabled)
			{
				if(buf.length() > 0)
				{
					buf.append(delimiter);
				}
				final Class classListener = listener.getClass();
				buf.append(classListener.getName());
				javaParameters.getClassPath().add(PathUtil.getJarPathForClass(classListener));
			}
		}
	}

	protected void configureClasspath(final OwnJavaParameters javaParameters) throws CantRunException
	{
		configureRTClasspath(javaParameters);
		RunConfigurationModule module = getConfiguration().getConfigurationModule();
		final String alternativeJreName = getConfiguration().isAlternativeJrePathEnabled() ? getConfiguration().getAlternativeJrePath() : null;
		final int pathType = OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS;
		if(configureByModule(module.getModule()))
		{
			JavaParametersUtil.configureModule(module, javaParameters, pathType, alternativeJreName);
		}
		else
		{
			JavaParametersUtil.configureProject(getConfiguration().getProject(), javaParameters, pathType, alternativeJreName);
		}
	}

	protected void createServerSocket(OwnJavaParameters javaParameters)
	{
		try
		{
			myServerSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
			javaParameters.getProgramParametersList().add("-socket" + myServerSocket.getLocalPort());
		}
		catch(IOException e)
		{
			LOG.error(e);
		}
	}

	protected boolean spansMultipleModules(final String qualifiedName)
	{
		if(qualifiedName != null)
		{
			final Project project = getConfiguration().getProject();
			final PsiJavaPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(qualifiedName);
			if(aPackage != null)
			{
				final TestSearchScope scope = getScope();
				if(scope != null)
				{
					final SourceScope sourceScope = scope.getSourceScope(getConfiguration());
					if(sourceScope != null)
					{
						final GlobalSearchScope configurationSearchScope = GlobalSearchScopesCore.projectTestScope(project).intersectWith(sourceScope.getGlobalSearchScope());
						final PsiDirectory[] directories = aPackage.getDirectories(configurationSearchScope);
						return Arrays.stream(directories).map(dir -> ModuleUtilCore.findModuleForFile(dir.getVirtualFile(), project)).filter(Objects::nonNull).distinct().count() > 1;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Configuration based on package which spans multiple modules
	 */
	protected boolean forkPerModule()
	{
		final String workingDirectory = getConfiguration().getWorkingDirectory();
		return getScope() != TestSearchScope.SINGLE_MODULE && ("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$").equals(workingDirectory) && spansMultipleModules(getConfiguration().getPackage());
	}

	protected void createTempFiles(OwnJavaParameters javaParameters)
	{
		try
		{
			myWorkingDirsFile = FileUtil.createTempFile("idea_working_dirs_" + getFrameworkId(), ".tmp", true);
			javaParameters.getProgramParametersList().add("@w@" + myWorkingDirsFile.getAbsolutePath());

			myTempFile = FileUtil.createTempFile("idea_" + getFrameworkId(), ".tmp", true);
			passTempFile(javaParameters.getProgramParametersList(), myTempFile.getAbsolutePath());
		}
		catch(Exception e)
		{
			LOG.error(e);
		}
	}

	protected void writeClassesPerModule(String packageName,
			OwnJavaParameters javaParameters,
			Map<Module, List<String>> perModule) throws FileNotFoundException, UnsupportedEncodingException, CantRunException
	{
		if(perModule != null)
		{
			final String classpath = getScope() == TestSearchScope.WHOLE_PROJECT ? null : javaParameters.getClassPath().getPathsString();

			final PrintWriter wWriter = new PrintWriter(myWorkingDirsFile, CharsetToolkit.UTF8);
			try
			{
				wWriter.println(packageName);
				for(Module module : perModule.keySet())
				{
					wWriter.println(module.getModuleDirPath());
					wWriter.println(module.getName());

					if(classpath == null)
					{
						final OwnJavaParameters parameters = new OwnJavaParameters();
						parameters.getClassPath().add(JavaSdkUtil.getJavaRtJarPath());
						configureRTClasspath(parameters);
						JavaParametersUtil.configureModule(module, parameters, OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS, getConfiguration().isAlternativeJrePathEnabled() ? getConfiguration()
								.getAlternativeJrePath() : null);
						wWriter.println(parameters.getClassPath().getPathsString());
					}
					else
					{
						wWriter.println(classpath);
					}

					final List<String> classNames = perModule.get(module);
					wWriter.println(classNames.size());
					for(String className : classNames)
					{
						wWriter.println(className);
					}
				}
			}
			finally
			{
				wWriter.close();
			}
		}
	}

	protected void deleteTempFiles()
	{
		if(myTempFile != null)
		{
			FileUtil.delete(myTempFile);
		}

		if(myWorkingDirsFile != null)
		{
			FileUtil.delete(myWorkingDirsFile);
		}
	}

}
