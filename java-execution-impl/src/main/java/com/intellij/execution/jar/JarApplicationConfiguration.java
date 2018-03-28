/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.jar;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jdom.Element;

import javax.annotation.Nullable;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.configurations.SearchScopeProvidingRunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author nik
 */
public class JarApplicationConfiguration extends LocatableConfigurationBase implements CommonJavaRunConfigurationParameters, SearchScopeProvidingRunProfile
{
	private static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
	private JarApplicationConfigurationBean myBean = new JarApplicationConfigurationBean();
	private Map<String, String> myEnvs = new LinkedHashMap<>();
	private JavaRunConfigurationModule myConfigurationModule;

	public JarApplicationConfiguration(Project project, ConfigurationFactory factory, String name)
	{
		super(project, factory, name);
		myConfigurationModule = new JavaRunConfigurationModule(project, true);
	}

	@Nonnull
	@Override
	public SettingsEditor<? extends RunConfiguration> getConfigurationEditor()
	{
		SettingsEditorGroup<JarApplicationConfiguration> group = new SettingsEditorGroup<>();
		group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new JarApplicationConfigurable(getProject()));
		JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
		group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
		return group;
	}

	@Override
	public void readExternal(@Nonnull Element element) throws InvalidDataException
	{
		super.readExternal(element);
		JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
		XmlSerializer.deserializeInto(myBean, element);
		EnvironmentVariablesComponent.readExternal(element, getEnvs());
		myConfigurationModule.readExternal(element);
	}

	@Override
	public RunConfiguration clone()
	{
		JarApplicationConfiguration clone = (JarApplicationConfiguration) super.clone();
		clone.myEnvs = new LinkedHashMap<>(myEnvs);
		clone.myConfigurationModule = new JavaRunConfigurationModule(getProject(), true);
		clone.myConfigurationModule.setModule(myConfigurationModule.getModule());
		clone.myBean = XmlSerializerUtil.createCopy(myBean);
		return clone;
	}

	public void setModule(Module module)
	{
		myConfigurationModule.setModule(module);
	}

	public Module getModule()
	{
		return myConfigurationModule.getModule();
	}

	@Override
	public void writeExternal(@Nonnull Element element) throws WriteExternalException
	{
		super.writeExternal(element);
		JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
		XmlSerializer.serializeInto(myBean, element, SERIALIZATION_FILTERS);
		EnvironmentVariablesComponent.writeExternal(element, getEnvs());
		if(myConfigurationModule.getModule() != null)
		{
			myConfigurationModule.writeExternal(element);
		}
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationException
	{
		JavaParametersUtil.checkAlternativeJRE(this);
		ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), null);
		File jarFile = new File(getJarPath());
		if(!jarFile.exists())
		{
			throw new RuntimeConfigurationWarning("JAR file '" + jarFile.getAbsolutePath() + "' doesn't exist");
		}
		JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
	}

	@Nonnull
	public Module[] getModules()
	{
		Module module = myConfigurationModule.getModule();
		return module != null ? new Module[]{module} : Module.EMPTY_ARRAY;
	}

	@javax.annotation.Nullable
	@Override
	public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment environment) throws ExecutionException
	{
		return new JarApplicationCommandLineState(this, environment);
	}

	public String getJarPath()
	{
		return myBean.JAR_PATH;
	}

	public void setJarPath(String jarPath)
	{
		myBean.JAR_PATH = jarPath;
	}

	@Override
	public void setVMParameters(@Nullable String value)
	{
		myBean.VM_PARAMETERS = value;
	}

	@Override
	public String getVMParameters()
	{
		return myBean.VM_PARAMETERS;
	}

	@Override
	public boolean isAlternativeJrePathEnabled()
	{
		return myBean.ALTERNATIVE_JRE_PATH_ENABLED;
	}

	@Override
	public void setAlternativeJrePathEnabled(boolean enabled)
	{
		myBean.ALTERNATIVE_JRE_PATH_ENABLED = enabled;
	}

	@javax.annotation.Nullable
	@Override
	public String getAlternativeJrePath()
	{
		return myBean.ALTERNATIVE_JRE_PATH;
	}

	@Override
	public void setAlternativeJrePath(String path)
	{
		myBean.ALTERNATIVE_JRE_PATH = path;
	}

	@javax.annotation.Nullable
	@Override
	public String getRunClass()
	{
		return null;
	}

	@javax.annotation.Nullable
	@Override
	public String getPackage()
	{
		return null;
	}

	@Override
	public void setProgramParameters(@javax.annotation.Nullable String value)
	{
		myBean.PROGRAM_PARAMETERS = value;
	}

	@javax.annotation.Nullable
	@Override
	public String getProgramParameters()
	{
		return myBean.PROGRAM_PARAMETERS;
	}

	@Override
	public void setWorkingDirectory(@Nullable String value)
	{
		myBean.WORKING_DIRECTORY = value;
	}

	@javax.annotation.Nullable
	@Override
	public String getWorkingDirectory()
	{
		return myBean.WORKING_DIRECTORY;
	}

	@Override
	public void setEnvs(@Nonnull Map<String, String> envs)
	{
		myEnvs.clear();
		myEnvs.putAll(envs);
	}

	@Nonnull
	@Override
	public Map<String, String> getEnvs()
	{
		return myEnvs;
	}

	@Override
	public void setPassParentEnvs(boolean passParentEnvs)
	{
		myBean.PASS_PARENT_ENVS = passParentEnvs;
	}

	@Override
	public boolean isPassParentEnvs()
	{
		return myBean.PASS_PARENT_ENVS;
	}

	private static class JarApplicationConfigurationBean
	{
		public String JAR_PATH = "";
		public String VM_PARAMETERS = "";
		public String PROGRAM_PARAMETERS = "";
		public String WORKING_DIRECTORY = "";
		public boolean ALTERNATIVE_JRE_PATH_ENABLED;
		public String ALTERNATIVE_JRE_PATH = "";
		public boolean PASS_PARENT_ENVS = true;
	}
}
