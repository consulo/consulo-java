/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.execution.impl.application;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.java.execution.impl.ConfigurationWithCommandLineShortener;
import com.intellij.java.execution.impl.JavaRunConfigurationExtensionManager;
import com.intellij.java.execution.impl.RunConfigurationExtension;
import com.intellij.java.execution.impl.SingleClassConfiguration;
import com.intellij.java.execution.impl.junit.RefactoringListeners;
import com.intellij.java.execution.impl.util.JavaParametersUtil;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.ShortenCommandLine;
import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.PathsList;
import consulo.java.debugger.impl.GenericDebugRunnerConfiguration;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import org.jdom.Element;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApplicationConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements CommonJavaRunConfigurationParameters, ConfigurationWithCommandLineShortener,
    SingleClassConfiguration, RefactoringListenerProvider, GenericDebugRunnerConfiguration {
  public String MAIN_CLASS_NAME;
  public String VM_PARAMETERS;
  public String PROGRAM_PARAMETERS;
  public String WORKING_DIRECTORY;
  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;
  public boolean ENABLE_SWING_INSPECTOR;
  public boolean INCLUDE_PROVIDED_SCOPE = false;

  private ShortenCommandLine myShortenCommandLine = null;

  public String ENV_VARIABLES;
  private final Map<String, String> myEnvs = new LinkedHashMap<>();
  public boolean PASS_PARENT_ENVS = true;

  public ApplicationConfiguration(final String name, final Project project, ApplicationConfigurationType applicationConfigurationType) {
    this(name, project, applicationConfigurationType.getConfigurationFactories()[0]);
  }

  protected ApplicationConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new JavaRunConfigurationModule(project, true), factory);
  }

  @Override
  public void setMainClass(final PsiClass psiClass) {
    final Module originalModule = getConfigurationModule().getModule();
    setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(psiClass));
    setModule(JavaExecutionUtil.findModule(psiClass));
    restoreOriginalModule(originalModule);
  }

  @Override
  public RunProfileState getState(@Nonnull final Executor executor, @Nonnull final ExecutionEnvironment env) throws ExecutionException {
    final JavaCommandLineState state = new JavaApplicationCommandLineState<>(this, env);
    JavaRunConfigurationModule module = getConfigurationModule();
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject(), module.getSearchScope()));
    return state;
  }

  @Override
  @Nonnull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<ApplicationConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new ApplicationConfigurable(getProject()));
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    final RefactoringElementListener listener = RefactoringListeners.
        getClassOrPackageListener(element, new RefactoringListeners.SingleClassConfigurationAccessor(this));
    return RunConfigurationExtension.wrapRefactoringElementListener(element, this, listener);
  }

  @Override
  @javax.annotation.Nullable
  public PsiClass getMainClass() {
    return getConfigurationModule().findClass(MAIN_CLASS_NAME);
  }

  @Override
  @javax.annotation.Nullable
  public String suggestedName() {
    if (MAIN_CLASS_NAME == null) {
      return null;
    }
    return JavaExecutionUtil.getPresentableClassName(MAIN_CLASS_NAME);
  }

  @Override
  public String getActionName() {
    if (MAIN_CLASS_NAME == null || MAIN_CLASS_NAME.length() == 0) {
      return null;
    }
    return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(MAIN_CLASS_NAME), 6) + ".main()";
  }

  @Override
  public void setMainClassName(final String qualifiedName) {
    MAIN_CLASS_NAME = qualifiedName;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(this);
    final JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass psiClass = configurationModule.checkModuleAndClassName(MAIN_CLASS_NAME, ExecutionBundle.message("no.main.class.specified.error.text"));
    if (!PsiMethodUtil.hasMainMethod(psiClass)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("main.method.not.found.in.class.error.message", MAIN_CLASS_NAME));
    }
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), configurationModule.getModule());
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Override
  public void setVMParameters(String value) {
    VM_PARAMETERS = value;
  }

  @Override
  public String getVMParameters() {
    return VM_PARAMETERS;
  }

  @Override
  public void setProgramParameters(String value) {
    PROGRAM_PARAMETERS = value;
  }

  @Override
  public String getProgramParameters() {
    return PROGRAM_PARAMETERS;
  }

  @Override
  public void setWorkingDirectory(String value) {
    WORKING_DIRECTORY = ExternalizablePath.urlValue(value);
  }

  @Override
  public String getWorkingDirectory() {
    return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    PASS_PARENT_ENVS = passParentEnvs;
  }

  @Override
  @Nonnull
  public Map<String, String> getEnvs() {
    return myEnvs;
  }

  @Override
  public void setEnvs(@Nonnull final Map<String, String> envs) {
    myEnvs.clear();
    myEnvs.putAll(envs);
  }

  @Override
  public boolean isPassParentEnvs() {
    return PASS_PARENT_ENVS;
  }

  @Override
  @javax.annotation.Nullable
  public String getRunClass() {
    return MAIN_CLASS_NAME;
  }

  @Override
  @javax.annotation.Nullable
  public String getPackage() {
    return null;
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    return ALTERNATIVE_JRE_PATH_ENABLED;
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    ALTERNATIVE_JRE_PATH_ENABLED = enabled;
  }

  @javax.annotation.Nullable
  @Override
  public String getAlternativeJrePath() {
    return ALTERNATIVE_JRE_PATH;
  }

  @Override
  public void setAlternativeJrePath(String path) {
    ALTERNATIVE_JRE_PATH = path;
  }

  public boolean isProvidedScopeIncluded() {
    return INCLUDE_PROVIDED_SCOPE;
  }

  public void setIncludeProvidedScope(boolean value) {
    INCLUDE_PROVIDED_SCOPE = value;
  }

  @Override
  public Collection<Module> getValidModules() {
    return JavaRunConfigurationModule.getModulesForClass(getProject(), MAIN_CLASS_NAME);
  }

  @Override
  public void readExternal(@Nonnull final Element element) {
    super.readExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
    DefaultJDOMExternalizer.readExternal(this, element);
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
    setShortenCommandLine(ShortenCommandLine.readShortenClasspathMethod(element));
  }

  @Override
  public void writeExternal(@Nonnull Element element) {
    super.writeExternal(element);

    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
    DefaultJDOMExternalizer.writeExternal(this, element);

    Map<String, String> envs = getEnvs();

    EnvironmentVariablesComponent.writeExternal(element, envs);

    ShortenCommandLine.writeShortenClasspathMethod(element, myShortenCommandLine);
  }

  @javax.annotation.Nullable
  @Override
  public ShortenCommandLine getShortenCommandLine() {
    return myShortenCommandLine;
  }

  @Override
  public void setShortenCommandLine(ShortenCommandLine mode) {
    myShortenCommandLine = mode;
  }

  public static class JavaApplicationCommandLineState<T extends ApplicationConfiguration> extends BaseJavaApplicationCommandLineState<T> {
    public JavaApplicationCommandLineState(@Nonnull final T configuration, final ExecutionEnvironment environment) {
      super(environment, configuration);
    }

    @Override
    protected OwnJavaParameters createJavaParameters() throws ExecutionException {
      final OwnJavaParameters params = new OwnJavaParameters();
      T configuration = getConfiguration();
      params.setShortenCommandLine(configuration.getShortenCommandLine(), configuration.getProject());

      final JavaRunConfigurationModule module = myConfiguration.getConfigurationModule();
      final String alternativeJreHome = myConfiguration.ALTERNATIVE_JRE_PATH_ENABLED ? myConfiguration.ALTERNATIVE_JRE_PATH : null;
      if (module.getModule() != null) {
        DumbService.getInstance(module.getProject()).runWithAlternativeResolveEnabled(() ->
        {
          int classPathType = JavaParametersUtil.getClasspathType(module, myConfiguration.MAIN_CLASS_NAME, false, myConfiguration.isProvidedScopeIncluded());
          JavaParametersUtil.configureModule(module, params, classPathType, alternativeJreHome);
        });
      } else {
        JavaParametersUtil.configureProject(module.getProject(), params, OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS, alternativeJreHome);
      }

      params.setMainClass(myConfiguration.MAIN_CLASS_NAME);

      setupJavaParameters(params);

      setupModulePath(params, module);

      return params;
    }

    @Override
    protected GeneralCommandLine createCommandLine() throws ExecutionException {
      GeneralCommandLine line = super.createCommandLine();
      Map<String, String> content = line.getUserData(OwnJdkUtil.COMMAND_LINE_CONTENT);
      if (content != null) {
        content.forEach((key, value) -> addConsoleFilters(new ArgumentFileFilter(key, value)));
      }
      return line;
    }

    @Nonnull
    @Override
    protected OSProcessHandler startProcess() throws ExecutionException {
      OSProcessHandler processHandler = super.startProcess();
      if (processHandler instanceof KillableProcessHandler && DebuggerSettings.getInstance().KILL_PROCESS_IMMEDIATELY) {
        ((KillableProcessHandler) processHandler).setShouldKillProcessSoftly(false);
      }
      return processHandler;
    }

    private static void setupModulePath(OwnJavaParameters params, JavaRunConfigurationModule module) {
      if (JavaSdkUtil.isJdkAtLeast(params.getJdk(), JavaSdkVersion.JDK_1_9)) {
        PsiJavaModule mainModule = DumbService.getInstance(module.getProject()).computeWithAlternativeResolveEnabled(() -> JavaModuleGraphUtil.findDescriptorByElement(module.findClass(params
            .getMainClass())));
        if (mainModule != null) {
          params.setModuleName(mainModule.getName());
          PathsList classPath = params.getClassPath(), modulePath = params.getModulePath();
          modulePath.addAll(classPath.getPathList());
          classPath.clear();
        }
      }
    }
  }
}