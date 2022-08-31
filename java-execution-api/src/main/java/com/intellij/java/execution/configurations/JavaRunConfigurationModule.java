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

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

  public static Collection<Module> getModulesForClass(@Nonnull final Project project, final String className) {
    if (project.isDefault()) return Arrays.asList(ModuleManager.getInstance(project).getModules());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final PsiClass[] possibleClasses = JavaPsiFacade.getInstance(project).findClasses(className, GlobalSearchScope.projectScope(project));

    final Set<Module> modules = new HashSet<Module>();
    for (PsiClass aClass : possibleClasses) {
      Module module = ModuleUtil.findModuleForPsiElement(aClass);
      if (module != null) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) {
      return Arrays.asList(ModuleManager.getInstance(project).getModules());
    }
    else {
      final Set<Module> result = new HashSet<Module>();
      for (Module module : modules) {
        ModuleUtil.collectModulesDependsOn(module, result);
      }
      return result;
    }
  }

  public PsiClass findNotNullClass(final String className) throws RuntimeConfigurationWarning {
    final PsiClass psiClass = findClass(className);
    if (psiClass == null) {
      throw new RuntimeConfigurationWarning(
        ExecutionBundle.message("class.not.found.in.module.error.message", className, getModuleName()));
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
