/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.util;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtil;
import consulo.java.execution.JavaExecutionBundle;
import consulo.java.execution.OwnSimpleJavaParameters;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import org.intellij.lang.annotations.MagicConstant;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * @author lex
 * @since Nov 26, 2003
 */
public class JavaParametersUtil {
  private JavaParametersUtil() {
  }

  public static void configureConfiguration(OwnSimpleJavaParameters parameters, CommonJavaRunConfigurationParameters configuration) {
    ProgramParametersUtil.configureConfiguration(parameters, configuration);

    Project project = configuration.getProject();
    Module module = ProgramParametersUtil.getModule(configuration);

    String alternativeJrePath = configuration.getAlternativeJrePath();
    if (alternativeJrePath != null) {
      configuration.setAlternativeJrePath(ProgramParametersUtil.expandPath(alternativeJrePath, null, project));
    }

    String vmParameters = configuration.getVMParameters();
    if (vmParameters != null) {
      vmParameters = ProgramParametersUtil.expandPath(vmParameters, module, project);

      for (Map.Entry<String, String> each : parameters.getEnv().entrySet()) {
        vmParameters = StringUtil.replace(vmParameters, "$" + each.getKey() + "$", each.getValue(), false); //replace env usages
      }
    }

    parameters.getVMParametersList().addParametersString(vmParameters);
  }

  @MagicConstant(valuesFromClass = OwnJavaParameters.class)
  public static int getClasspathType(final RunConfigurationModule configurationModule, final String mainClassName, final boolean classMustHaveSource) throws CantRunException {
    return getClasspathType(configurationModule, mainClassName, classMustHaveSource, false);
  }

  @MagicConstant(valuesFromClass = OwnJavaParameters.class)
  public static int getClasspathType(final RunConfigurationModule configurationModule,
                                     final String mainClassName,
                                     final boolean classMustHaveSource,
                                     final boolean includeProvidedDependencies) throws CantRunException {
    final Module module = configurationModule.getModule();
    if (module == null) {
      throw CantRunException.noModuleConfigured(configurationModule.getModuleName());
    }
    Boolean inProduction = isClassInProductionSources(mainClassName, module);
    if (inProduction == null) {
      if (!classMustHaveSource) {
        return OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS;
      }
      throw CantRunException.classNotFound(mainClassName, module);
    }

    return inProduction ? (includeProvidedDependencies ? OwnJavaParameters.JDK_AND_CLASSES_AND_PROVIDED : OwnJavaParameters.JDK_AND_CLASSES) : OwnJavaParameters.JDK_AND_CLASSES_AND_TESTS;
  }

  /**
   * @return null if class not found
   */
  @Nullable
  public static Boolean isClassInProductionSources(@Nonnull String mainClassName, @Nonnull Module module) {
    final PsiClass psiClass = JavaExecutionUtil.findMainClass(module, mainClassName);
    if (psiClass == null) {
      return null;
    }
    final PsiFile psiFile = psiClass.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    Module classModule = psiClass.isValid() ? ModuleUtilCore.findModuleForPsiElement(psiClass) : null;
    if (classModule == null) {
      classModule = module;
    }
    ModuleFileIndex fileIndex = ModuleRootManager.getInstance(classModule).getFileIndex();
    if (fileIndex.isInSourceContent(virtualFile)) {
      return !fileIndex.isInTestSourceContent(virtualFile);
    }
    final List<OrderEntry> entriesForFile = fileIndex.getOrderEntriesForFile(virtualFile);
    for (OrderEntry entry : entriesForFile) {
      if (entry instanceof ExportableOrderEntry && ((ExportableOrderEntry) entry).getScope() == DependencyScope.TEST) {
        return false;
      }
    }
    return true;
  }

  public static void configureModule(final RunConfigurationModule runConfigurationModule,
                                     final OwnJavaParameters parameters,
                                     @MagicConstant(valuesFromClass = OwnJavaParameters.class) final int classPathType,
                                     @Nullable String alternativeJreName) throws CantRunException {
    Module module = runConfigurationModule.getModule();
    if (module == null) {
      throw CantRunException.noModuleConfigured(runConfigurationModule.getModuleName());
    }
    configureModule(module, parameters, classPathType, alternativeJreName);
  }

  public static void configureModule(@Nonnull Module module,
                                     @Nonnull OwnJavaParameters parameters,
                                     @MagicConstant(valuesFromClass = OwnJavaParameters.class) int classPathType,
                                     @Nullable String alternativeJreName) throws CantRunException {
    parameters.configureByModule(module, classPathType, createModuleJdk(module, (classPathType & OwnJavaParameters.TESTS_ONLY) == 0, alternativeJreName));
  }

  public static void configureProject(Project project,
                                      final OwnJavaParameters parameters,
                                      @MagicConstant(valuesFromClass = OwnJavaParameters.class) final int classPathType,
                                      @Nullable String alternativeJreName) throws CantRunException {
    parameters.configureByProject(project, classPathType, createProjectJdk(project, alternativeJreName));
  }

  public static Sdk createModuleJdk(final Module module, boolean productionOnly, @Nullable String jreHome) throws CantRunException {
    return jreHome == null ? OwnJavaParameters.getValidJdkToRunModule(module, productionOnly) : createAlternativeJdk(jreHome);
  }

  public static Sdk createProjectJdk(final Project project, @Nullable String jreHome) throws CantRunException {
    return jreHome == null ? createProjectJdk(project) : createAlternativeJdk(jreHome);
  }

  private static Sdk createProjectJdk(final Project project) throws CantRunException {
    final Sdk jdk = PathUtilEx.getAnyJdk(project);
    if (jdk == null) {
      throw CantRunException.noJdkConfigured();
    }
    return jdk;
  }

  private static Sdk createAlternativeJdk(@Nonnull String jreHome) throws CantRunException {
    final Sdk configuredJdk = SdkTable.getInstance().findSdk(jreHome);
    if (configuredJdk != null) {
      return configuredJdk;
    }

    if (!OwnJdkUtil.checkForJre(jreHome)) {
      throw new CantRunException(JavaExecutionBundle.message("jre.path.is.not.valid.jre.home.error.message", jreHome));
    }

    final JavaSdk javaSdk = JavaSdk.getInstance();
    return javaSdk.createJdk(ObjectUtil.notNull(javaSdk.getVersionString(jreHome), ""), jreHome);
  }

  public static void checkAlternativeJRE(@Nonnull CommonJavaRunConfigurationParameters configuration) throws RuntimeConfigurationWarning {
    if (configuration.isAlternativeJrePathEnabled()) {
      checkAlternativeJRE(configuration.getAlternativeJrePath());
    }
  }

  public static void checkAlternativeJRE(@javax.annotation.Nullable String jrePath) throws RuntimeConfigurationWarning {
    if (StringUtil.isEmptyOrSpaces(jrePath) || SdkTable.getInstance().findSdk(jrePath) == null && !OwnJdkUtil.checkForJre(jrePath)) {
      throw new RuntimeConfigurationWarning(JavaExecutionBundle.message("jre.path.is.not.valid.jre.home.error.message", jrePath));
    }
  }
}