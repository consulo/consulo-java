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
package com.intellij.execution.actions;

import java.util.Set;

import javax.annotation.Nonnull;

import com.intellij.execution.Location;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.annotations.RequiredDispatchThread;


public abstract class AbstractExcludeFromRunAction<T extends ModuleBasedConfiguration<JavaRunConfigurationModule>> extends AnAction
{
	private static final Logger LOG = Logger.getInstance(AbstractExcludeFromRunAction.class);

	protected abstract Set<String> getPattern(T configuration);

	protected abstract boolean isPatternBasedConfiguration(RunConfiguration configuration);

	@RequiredDispatchThread
	@Override
	public void actionPerformed(@Nonnull AnActionEvent e)
	{
		final Project project = e.getData(CommonDataKeys.PROJECT);
		LOG.assertTrue(project != null);
		final T configuration = (T) e.getData(RunConfiguration.DATA_KEY);
		LOG.assertTrue(configuration != null);
		final GlobalSearchScope searchScope = configuration.getConfigurationModule().getSearchScope();
		final AbstractTestProxy testProxy = e.getData(AbstractTestProxy.DATA_KEY);
		LOG.assertTrue(testProxy != null);
		final String qualifiedName = ((PsiClass) testProxy.getLocation(project, searchScope).getPsiElement()).getQualifiedName();
		getPattern(configuration).remove(qualifiedName);
	}

	@RequiredDispatchThread
	@Override
	public void update(@Nonnull AnActionEvent e)
	{
		final Presentation presentation = e.getPresentation();
		presentation.setVisible(false);
		final Project project = e.getData(CommonDataKeys.PROJECT);
		if(project != null)
		{
			final RunConfiguration configuration = e.getData(RunConfiguration.DATA_KEY);
			if(isPatternBasedConfiguration(configuration))
			{
				final AbstractTestProxy testProxy = e.getData(AbstractTestProxy.DATA_KEY);
				if(testProxy != null)
				{
					final Location location = testProxy.getLocation(project, ((T) configuration).getConfigurationModule().getSearchScope());
					if(location != null)
					{
						final PsiElement psiElement = location.getPsiElement();
						if(psiElement instanceof PsiClass && getPattern((T) configuration).contains(((PsiClass) psiElement).getQualifiedName()))
						{
							presentation.setVisible(true);
						}
					}
				}
			}
		}
	}
}
