/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.execution.impl.application;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.ShortenCommandLine;
import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.execution.impl.ConfigurationWithCommandLineShortener;
import com.intellij.java.execution.impl.JavaRunConfigurationExtensionManager;
import com.intellij.java.execution.impl.RunConfigurationExtension;
import com.intellij.java.execution.impl.SingleClassConfiguration;
import com.intellij.java.execution.impl.junit.RefactoringListeners;
import com.intellij.java.execution.impl.util.JavaParametersUtil;
import com.intellij.java.language.impl.projectRoots.ex.JavaSdkUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ReadAction;
import consulo.execution.CantRunException;
import consulo.execution.ProgramRunnerUtil;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.configuration.*;
import consulo.execution.configuration.fragment.EnvironmentFragmentState;
import consulo.execution.configuration.fragment.ProgramParametersFragmentState;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.WorkingDirectoryFragmentState;
import consulo.execution.executor.Executor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.console.ArgumentFileFilter;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.execution.util.ProgramParametersUtil;
import consulo.java.debugger.impl.GenericDebugRunnerConfiguration;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.execution.configurations.fragment.JreFragmentState;
import consulo.java.execution.configurations.fragment.VmParametersFragmentState;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringListenerProvider;
import consulo.language.psi.PsiElement;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.BitUtil;
import consulo.virtualFileSystem.util.PathsList;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ApplicationConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements CommonJavaRunConfigurationParameters, ConfigurationWithCommandLineShortener,
    SingleClassConfiguration, RefactoringListenerProvider, GenericDebugRunnerConfiguration {
  public ApplicationConfiguration(String name, Project project, ApplicationConfigurationType applicationConfigurationType) {
    this(name, project, applicationConfigurationType.getConfigurationFactories()[0]);
  }

  protected ApplicationConfiguration(String name, Project project, ConfigurationFactory factory) {
    super(name, new JavaRunConfigurationModule(project, true), factory);
  }

  @Override
  public void setMainClass(PsiClass psiClass) {
    Module originalModule = getConfigurationModule().getModule();
    setMainClassName(psiClass.getQualifiedName());
    setModule(JavaExecutionUtil.findModule(psiClass));
    restoreOriginalModule(originalModule);
  }

  @Override
  protected Class<ApplicationConfigurationOptions> getOptionsClass() {
    return ApplicationConfigurationOptions.class;
  }

  @Override
  public ApplicationConfigurationOptions getOptions() {
    return (ApplicationConfigurationOptions) super.getOptions();
  }

  @Override
  protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
    super.convertLegacyState(converter);

    ApplicationConfigurationLegacyState old = converter.readXml(ApplicationConfigurationLegacyState.class);

    ApplicationConfigurationOptions options = new ApplicationConfigurationOptions();
    options.setMainClass(old.MAIN_CLASS_NAME);
    options.setIncludeProvidedScope(old.INCLUDE_PROVIDED_SCOPE);
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
    return List.of(new ApplicationMainClassFragment(), new ApplicationIncludeProvidedScopeFragment());
  }

  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException {
    JavaCommandLineState state = new JavaApplicationCommandLineState<>(this, env);
    JavaRunConfigurationModule module = getConfigurationModule();
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject(), module.getSearchScope()));
    return state;
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    RefactoringElementListener listener =
        RefactoringListeners.getClassOrPackageListener(element, new RefactoringListeners.SingleClassConfigurationAccessor(this));
    return RunConfigurationExtension.wrapRefactoringElementListener(element, this, listener);
  }

  @Override
  @Nullable
  public PsiClass getMainClass() {
    return DumbService.getInstance(getProject()).computeWithAlternativeResolveEnabled(() -> getConfigurationModule().findClass(getMainClassName()));
  }

  @Nullable
  public String getMainClassName() {
    return getOptions().getMainClass();
  }

  @Override
  public boolean includeTestScope() {
    return ReadAction.compute(() -> {
      try {
        JavaRunConfigurationModule module = getConfigurationModule();
        String runClass = getRunClass();
        if (module != null && runClass != null) {
          int classpathType = JavaParametersUtil.getClasspathType(module, runClass, false);
          // if there no test scope - we don't need compile it
          if (!BitUtil.isSet(classpathType, OwnJavaParameters.TESTS_ONLY)) {
            return false;
          }
        }
      }
      catch (CantRunException ignored) {
      }

      return true;
    });
  }

  @Override
  @Nullable
  public String suggestedName() {
    String mainClassName = getMainClassName();
    if (mainClassName == null) {
      return null;
    }
    return JavaExecutionUtil.getPresentableClassName(mainClassName);
  }

  @Override
  public String getActionName() {
    String mainClassName = getMainClassName();
    if (mainClassName == null) {
      return null;
    }
    return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(mainClassName), 6) + ".main()";
  }

  @Override
  public void setMainClassName(@Nullable String qualifiedName) {
    getOptions().setMainClass(qualifiedName);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(this);
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    String mainClassName = getMainClassName();
    PsiClass psiClass = configurationModule.checkModuleAndClassName(
      mainClassName,
      ExecutionLocalize.noMainClassSpecifiedErrorText().get()
    );
    if (!PsiMethodUtil.hasMainMethod(psiClass)) {
      throw new RuntimeConfigurationWarning(ExecutionLocalize.mainMethodNotFoundInClassErrorMessage(mainClassName));
    }
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), configurationModule.getModule());
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Override
  @Nullable
  public String getRunClass() {
    PsiClass mainClass = getMainClass();
    if (mainClass == null) {
      return null;
    }
    return JavaExecutionUtil.getRuntimeQualifiedName(mainClass);
  }

  @Override
  @Nullable
  public String getPackage() {
    return null;
  }

  public boolean isProvidedScopeIncluded() {
    return getOptions().isIncludeProvidedScope();
  }

  public void setIncludeProvidedScope(boolean value) {
    getOptions().setIncludeProvidedScope(value);
  }

  @Override
  @RequiredReadAction
  public Collection<Module> getValidModules() {
    return JavaRunConfigurationModule.getModulesForClass(getProject(), getMainClassName());
  }

  public static class JavaApplicationCommandLineState<T extends ApplicationConfiguration> extends BaseJavaApplicationCommandLineState<T> {
    public JavaApplicationCommandLineState(T configuration, ExecutionEnvironment environment) {
      super(environment, configuration);
    }

    @Override
    protected OwnJavaParameters createJavaParameters() throws ExecutionException {
      OwnJavaParameters params = new OwnJavaParameters();
      T configuration = getConfiguration();

      String mainClass = ReadAction.compute(myConfiguration::getRunClass);

      JavaRunConfigurationModule module = myConfiguration.getConfigurationModule();
      String alternativeJreHome = myConfiguration.getAlternativeJrePath();
      if (module.getModule() != null) {
        DumbService.getInstance(module.getProject()).runWithAlternativeResolveEnabled(() -> {
          if (mainClass == null) {
            throw new CantRunException(ExecutionLocalize.noMainClassSpecifiedErrorText());
          }
          int classPathType = JavaParametersUtil.getClasspathType(module, mainClass, false, myConfiguration.isProvidedScopeIncluded());
          JavaParametersUtil.configureModule(module, params, classPathType, alternativeJreHome);
        });
      } else {
        JavaParametersUtil.configureProject(module.getProject(), params, OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS, alternativeJreHome);
      }

      // we need set #setShortenCommandLine after jdk set since, some default values checked
      params.setShortenCommandLine(configuration.getShortenCommandLine(), configuration.getProject());

      params.setMainClass(mainClass);

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

    @Override
    protected void buildProcessHandler(ProcessHandlerBuilder builder) throws ExecutionException {
      super.buildProcessHandler(builder);

      if (DebuggerSettings.getInstance().KILL_PROCESS_IMMEDIATELY) {
        builder.shouldKillProcessSoftly(false);
      }
    }

    private static void setupModulePath(OwnJavaParameters params, JavaRunConfigurationModule module) {
      if (JavaSdkUtil.isJdkAtLeast(params.getJdk(), JavaSdkVersion.JDK_1_9)) {
        PsiJavaModule mainModule = DumbService.getInstance(module.getProject()).computeWithAlternativeResolveEnabled(
            () -> JavaModuleGraphUtil.findDescriptorByElement(module.findClass(params.getMainClass()))
        );
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