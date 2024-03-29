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
package com.intellij.java.execution.impl.junit;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.RuntimeConfigurationProducer;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.test.TestSearchScope;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;

/**
 * @author spleaner
 */
public abstract class JavaRuntimeConfigurationProducerBase extends RuntimeConfigurationProducer {

  protected JavaRuntimeConfigurationProducerBase(final ConfigurationType configurationType) {
    super(configurationType);
  }

  protected static PsiMethod getContainingMethod(PsiElement element) {
    while (element != null)
      if (element instanceof PsiMethod) break;
      else element = element.getParent();
    return (PsiMethod) element;
  }

  @Nullable
  public static PsiJavaPackage checkPackage(final PsiElement element) {
    if (element == null || !element.isValid()) return null;
    final Project project = element.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (element instanceof PsiJavaPackage) {
      final PsiJavaPackage aPackage = (PsiJavaPackage) element;
      final PsiDirectory[] directories = aPackage.getDirectories(GlobalSearchScope.projectScope(project));
      for (final PsiDirectory directory : directories) {
        if (isSource(directory, fileIndex)) return aPackage;
      }
      return null;
    } else if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory) element;
      if (isSource(directory, fileIndex)) {
        return JavaDirectoryService.getInstance().getPackage(directory);
      }
    }

    return null;
  }

  private static boolean isSource(final PsiDirectory directory, final ProjectFileIndex fileIndex) {
    final VirtualFile virtualFile = directory.getVirtualFile();
    return fileIndex.getSourceRootForFile(virtualFile) != null;
  }

  protected TestSearchScope setupPackageConfiguration(ConfigurationContext context, Project project, ModuleBasedConfiguration configuration, TestSearchScope scope) {
    if (scope != TestSearchScope.WHOLE_PROJECT) {
      if (!setupConfigurationModule(context, configuration)) {
        return TestSearchScope.WHOLE_PROJECT;
      }
    }
    return scope;
  }

  protected boolean setupConfigurationModule(@Nullable ConfigurationContext context, ModuleBasedConfiguration configuration) {
    if (context != null) {
      final RunnerAndConfigurationSettings template = context.getRunManager().getConfigurationTemplate(getConfigurationFactory());
      final Module contextModule = context.getModule();
      final Module predefinedModule = ((ModuleBasedConfiguration) template.getConfiguration()).getConfigurationModule().getModule();
      if (predefinedModule != null) {
        configuration.setModule(predefinedModule);
        return true;
      }
      final Module module = findModule(configuration, contextModule);
      if (module != null) {
        configuration.setModule(module);
        return true;
      }
    }
    return false;
  }

  protected Module findModule(ModuleBasedConfiguration configuration, Module contextModule) {
    if (configuration.getConfigurationModule().getModule() == null && contextModule != null) {
      return contextModule;
    }
    return null;
  }
}
