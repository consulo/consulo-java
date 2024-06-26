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
package com.intellij.java.execution.configurations;

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.configuration.RunConfigurationModule;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.localize.ExecutionLocalize;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author spleaner
 */
public class JavaRunConfigurationModule extends RunConfigurationModule {

  private final boolean myClassesInLibraries;

  public JavaRunConfigurationModule(final Project project, final boolean classesInLibs) {
    super(project);

    myClassesInLibraries = classesInLibs;
  }

  @Nullable
  public PsiClass findClass(final String qualifiedName) {
    if (qualifiedName == null) return null;
    return JavaExecutionUtil.findMainClass(getProject(), qualifiedName, getSearchScope());
  }

  public GlobalSearchScope getSearchScope() {
    final Module module = getModule();
    if (module != null) {
      return myClassesInLibraries ? GlobalSearchScope.moduleRuntimeScope(module, true) : GlobalSearchScope.moduleWithDependenciesScope(module);
    }
    return myClassesInLibraries ? GlobalSearchScope.allScope(getProject()) : GlobalSearchScope.projectScope(getProject());
  }

  @RequiredReadAction
  public static Collection<Module> getModulesForClass(@Nonnull final Project project, final String className) {
    if (project.isDefault()) return Arrays.asList(ModuleManager.getInstance(project).getModules());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final PsiClass[] possibleClasses = JavaPsiFacade.getInstance(project).findClasses(className, GlobalSearchScope.projectScope(project));

    final Set<Module> modules = new HashSet<>();
    for (PsiClass aClass : possibleClasses) {
      Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
      if (module != null) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) {
      return Arrays.asList(ModuleManager.getInstance(project).getModules());
    } else {
      final Set<Module> result = new HashSet<>();
      for (Module module : modules) {
        ModuleUtilCore.collectModulesDependsOn(module, result);
      }
      return result;
    }
  }

  public PsiClass findNotNullClass(final String className) throws RuntimeConfigurationWarning {
    final PsiClass psiClass = findClass(className);
    if (psiClass == null) {
      throw new RuntimeConfigurationWarning(
        ExecutionLocalize.classNotFoundInModuleErrorMessage(className, getModuleName()).get()
      );
    }
    return psiClass;
  }

  public PsiClass checkModuleAndClassName(final String className, final String expectedClassMessage) throws RuntimeConfigurationException {
    checkForWarning();
    return checkClassName(className, expectedClassMessage);
  }


  public PsiClass checkClassName(final String className, final String errorMessage) throws RuntimeConfigurationException {
    if (className == null || className.length() == 0) {
      throw new RuntimeConfigurationError(errorMessage);
    }
    return findNotNullClass(className);
  }
}
