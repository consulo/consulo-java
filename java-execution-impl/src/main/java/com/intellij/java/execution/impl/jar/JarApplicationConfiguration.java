/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.execution.impl.jar;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.execution.impl.JavaRunConfigurationExtensionManager;
import com.intellij.java.execution.impl.util.JavaParametersUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.fragment.EnvironmentFragmentState;
import consulo.execution.configuration.fragment.ProgramParametersFragmentState;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.WorkingDirectoryFragmentState;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.util.ProgramParametersUtil;
import consulo.java.execution.configurations.fragment.JreFragmentState;
import consulo.java.execution.configurations.fragment.VmParametersFragmentState;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class JarApplicationConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements CommonJavaRunConfigurationParameters {
  public JarApplicationConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(name, new JavaRunConfigurationModule(project, true), factory);
  }

  @Override
  protected Class<JarApplicationConfigurationOptions> getOptionsClass() {
    return JarApplicationConfigurationOptions.class;
  }

  @Override
  public JarApplicationConfigurationOptions getOptions() {
    return (JarApplicationConfigurationOptions) super.getOptions();
  }

  @Override
  protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
    super.convertLegacyState(converter);

    JarApplicationConfigurationLegacyState old = converter.readXml(JarApplicationConfigurationLegacyState.class);

    JarApplicationConfigurationOptions options = new JarApplicationConfigurationOptions();
    options.setJarPath(old.JAR_PATH);
    converter.setOptions(options);

    converter.addFragment(new ProgramParametersFragmentState(old.PROGRAM_PARAMETERS));
    converter.addFragment(new WorkingDirectoryFragmentState(old.WORKING_DIRECTORY));
    converter.addFragment(new EnvironmentFragmentState(converter.readEnvs(), old.PASS_PARENT_ENVS));
    converter.addFragment(new VmParametersFragmentState(old.VM_PARAMETERS));
    if (old.ALTERNATIVE_JRE_PATH_ENABLED) {
      converter.addFragment(new JreFragmentState(old.ALTERNATIVE_JRE_PATH));
    }
  }

  @Override
  public List<RunConfigurationFragment<?>> createFragments() {
    return List.of(new JarPathFragment());
  }

  @Override
  @RequiredReadAction
  public Collection<Module> getValidModules() {
    return getAllModules();
  }

  public @Nullable Module getModule() {
    return getConfigurationModule().getModule();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(this);
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), null);
    File jarFile = new File(getJarPath());
    if (!jarFile.exists()) {
      throw new RuntimeConfigurationWarning("JAR file '" + jarFile.getAbsolutePath() + "' doesn't exist");
    }
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Nullable
  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment environment) throws ExecutionException {
    return new JarApplicationCommandLineState(this, environment);
  }

  public String getJarPath() {
    return StringUtil.notNullize(getOptions().getJarPath());
  }

  public void setJarPath(@Nullable String jarPath) {
    getOptions().setJarPath(jarPath);
  }

  @Nullable
  @Override
  public String getRunClass() {
    return null;
  }

  @Nullable
  @Override
  public String getPackage() {
    return null;
  }
}
