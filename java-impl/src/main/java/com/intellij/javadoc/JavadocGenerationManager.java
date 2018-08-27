/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.javadoc;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jdom.Element;
import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializer;

@Singleton
@State(name = "JavadocGenerationManager", storages = @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/misc.xml"))
public final class JavadocGenerationManager implements PersistentStateComponent<Element>
{
	private final JavadocConfiguration myConfiguration = new JavadocConfiguration();
	private final Project myProject;

	public static JavadocGenerationManager getInstance(@Nonnull Project project)
	{
		return ServiceManager.getService(project, JavadocGenerationManager.class);
	}

	@Inject
	JavadocGenerationManager(Project project)
	{
		myProject = project;
	}

	@Override
	public Element getState()
	{
		return XmlSerializer.serialize(myConfiguration, JavadocConfiguration.FILTER);
	}

	@Override
	public void loadState(Element state)
	{
		XmlSerializer.deserializeInto(myConfiguration, state);
	}

	@Nonnull
	public JavadocConfiguration getConfiguration()
	{
		return myConfiguration;
	}

	public void generateJavadoc(AnalysisScope scope)
	{
		try
		{
			JavadocGeneratorRunProfile profile = new JavadocGeneratorRunProfile(myProject, scope, myConfiguration);
			ExecutionEnvironmentBuilder.create(myProject, DefaultRunExecutor.getRunExecutorInstance(), profile).buildAndExecute();
		}
		catch(ExecutionException e)
		{
			ExecutionErrorDialog.show(e, CommonBundle.getErrorTitle(), myProject);
		}
	}
}