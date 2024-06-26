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
package com.intellij.java.execution.impl.actions;

import consulo.execution.action.Location;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.test.AbstractTestProxy;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;

import jakarta.annotation.Nonnull;
import java.util.Set;

public abstract class AbstractExcludeFromRunAction<T extends ModuleBasedConfiguration<JavaRunConfigurationModule>> extends AnAction {
  private static final Logger LOG = Logger.getInstance(AbstractExcludeFromRunAction.class);

  protected abstract Set<String> getPattern(T configuration);

  protected abstract boolean isPatternBasedConfiguration(RunConfiguration configuration);

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final Project project = e.getData(Project.KEY);
    LOG.assertTrue(project != null);
    @SuppressWarnings("unchecked")
    final T configuration = (T) e.getData(RunConfiguration.DATA_KEY);
    LOG.assertTrue(configuration != null);
    final GlobalSearchScope searchScope = configuration.getConfigurationModule().getSearchScope();
    final AbstractTestProxy testProxy = e.getData(AbstractTestProxy.KEY);
    LOG.assertTrue(testProxy != null);
    final String qualifiedName = ((PsiClass) testProxy.getLocation(project, searchScope).getPsiElement()).getQualifiedName();
    getPattern(configuration).remove(qualifiedName);
  }

  @RequiredUIAccess
  @Override
  public void update(@Nonnull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(false);
    final Project project = e.getData(Project.KEY);
    if (project != null) {
      final RunConfiguration configuration = e.getData(RunConfiguration.DATA_KEY);
      if (isPatternBasedConfiguration(configuration)) {
        final AbstractTestProxy testProxy = e.getData(AbstractTestProxy.KEY);
        if (testProxy != null) {
          final Location location = testProxy.getLocation(project, ((T) configuration).getConfigurationModule().getSearchScope());
          if (location != null) {
            final PsiElement psiElement = location.getPsiElement();
            if (psiElement instanceof PsiClass psiClass && getPattern((T) configuration).contains(psiClass.getQualifiedName())) {
              presentation.setVisible(true);
            }
          }
        }
      }
    }
  }
}
