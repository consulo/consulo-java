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
package com.intellij.java.execution;

import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.PsiClassUtil;
import consulo.annotation.DeprecationInfo;
import consulo.annotation.access.RequiredReadAction;
import consulo.dataContext.DataContext;
import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.runner.RunnerRegistry;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.TextConsoleBuilder;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author spleaner
 */
public class JavaExecutionUtil {
  private JavaExecutionUtil() {
  }

  public static boolean executeRun(@Nonnull final Project project, String contentName, Image icon, final DataContext dataContext) throws ExecutionException {
    return executeRun(project, contentName, icon, dataContext, null);
  }

  public static boolean executeRun(@Nonnull final Project project, String contentName, Image icon, DataContext dataContext, Filter[] filters) throws ExecutionException {
    final OwnJavaParameters cmdLine = dataContext.getData(OwnJavaParameters.JAVA_PARAMETERS);
    final DefaultRunProfile profile = new DefaultRunProfile(project, cmdLine, contentName, icon, filters);
    final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(DefaultRunExecutor.EXECUTOR_ID, profile);
    if (runner != null) {
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      runner.execute(new ExecutionEnvironment(profile, executor, project, null));
      return true;
    }

    return false;
  }

  @RequiredReadAction
  public static Module findModule(
    final Module contextModule,
    final Set<String> patterns,
    final Project project,
    Condition<PsiClass> isTestMethod
  ) {
    final Set<Module> modules = new HashSet<>();
    for (String className : patterns) {
      final PsiClass psiClass = findMainClass(project, className.contains(",") ? className.substring(0, className.indexOf(',')) : className, GlobalSearchScope.allScope(project));
      if (psiClass != null && isTestMethod.value(psiClass)) {
        modules.add(ModuleUtilCore.findModuleForPsiElement(psiClass));
      }
    }

    if (modules.size() == 1) {
      final Module nextModule = modules.iterator().next();
      if (nextModule != null) {
        return nextModule;
      }
    }
    if (contextModule != null && modules.size() > 1) {
      final HashSet<Module> moduleDependencies = new HashSet<>();
      ModuleUtilCore.getDependencies(contextModule, moduleDependencies);
      if (moduleDependencies.containsAll(modules)) {
        return contextModule;
      }
    }
    return null;
  }

  private static final class DefaultRunProfile implements RunProfile {
    private final OwnJavaParameters myParameters;
    private final String myContentName;
    private final Filter[] myFilters;
    private final Project myProject;
    private final Image myIcon;

    public DefaultRunProfile(final Project project, final OwnJavaParameters parameters, final String contentName, final Image icon, Filter[] filters) {
      myProject = project;
      myParameters = parameters;
      myContentName = contentName;
      myFilters = filters;
      myIcon = icon;
    }

    @Override
    public Image getIcon() {
      return myIcon;
    }

    @Override
    public RunProfileState getState(@Nonnull final Executor executor, @Nonnull final ExecutionEnvironment env) throws ExecutionException {
      final JavaCommandLineState state = new JavaCommandLineState(env) {
        @Override
        protected OwnJavaParameters createJavaParameters() {
          return myParameters;
        }
      };
      final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(myProject);
      if (myFilters != null) {
        builder.filters(myFilters);
      }
      state.setConsoleBuilder(builder);
      return state;
    }

    @Override
    public String getName() {
      return myContentName;
    }
  }

  @Nullable
  public static String getRuntimeQualifiedName(@Nonnull final PsiClass aClass) {
    return ClassUtil.getJVMClassName(aClass);
  }

  @Nullable
  public static String getPresentableClassName(@Nullable String rtClassName) {
    if (StringUtil.isEmpty(rtClassName)) {
      return null;
    }

    int lastDot = rtClassName.lastIndexOf('.');
    return lastDot == -1 || lastDot == rtClassName.length() - 1 ? rtClassName : rtClassName.substring(lastDot + 1, rtClassName.length());
  }

  /**
   * {@link JavaExecutionUtil#getPresentableClassName(String)}
   */
  @DeprecationInfo("Use JavaExecutionUtil#getPresentableClassName(java.lang.String)")
  @Deprecated
  @Nullable
  public static String getPresentableClassName(final String rtClassName, final JavaRunConfigurationModule configurationModule) {
    return getPresentableClassName(rtClassName);
  }

  @RequiredReadAction
  public static Module findModule(@Nonnull final PsiClass psiClass) {
    return ModuleUtilCore.findModuleForPsiElement(psiClass);
  }

  @Nullable
  public static PsiClass findMainClass(final Module module, final String mainClassName) {
    return findMainClass(module.getProject(), mainClassName, GlobalSearchScope.moduleRuntimeScope(module, true));
  }

  @Nullable
  public static PsiClass findMainClass(final Project project, final String mainClassName, final GlobalSearchScope scope) {
    if (project.isDefault()) {
      return null;
    }
    final PsiManager psiManager = PsiManager.getInstance(project);
    final String shortName = StringUtil.getShortName(mainClassName);
    final String packageName = StringUtil.getPackageName(mainClassName);
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
    final PsiClass psiClass = psiFacade.findClass(StringUtil.getQualifiedName(packageName, shortName.replace('$', '.')), scope);
    return psiClass == null ? psiFacade.findClass(mainClassName, scope) : psiClass;
  }


  public static boolean isNewName(final String name) {
    return name == null || name.startsWith(ExecutionLocalize.runConfigurationUnnamedNamePrefix().get());
  }

  public static Location stepIntoSingleClass(final Location location) {
    PsiElement element = location.getPsiElement();
    if (!(element instanceof PsiClassOwner)) {
      if (PsiTreeUtil.getParentOfType(element, PsiClass.class) != null) {
        return location;
      }
      element = PsiTreeUtil.getParentOfType(element, PsiClassOwner.class);
      if (element == null) {
        return location;
      }
    }
    final PsiClassOwner psiFile = (PsiClassOwner) element;
    final PsiClass[] classes = psiFile.getClasses();
    if (classes.length != 1) {
      return location;
    }
    return PsiLocation.fromPsiElement(classes[0]);
  }

  public static String getShortClassName(final String fqName) {
    if (fqName == null) {
      return "";
    }
    return StringUtil.getShortName(fqName);
  }

  public static boolean isRunnableClass(final PsiClass aClass) {
    return PsiClassUtil.isRunnableClass(aClass, true);
  }
}
