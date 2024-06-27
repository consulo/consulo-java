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
package com.intellij.java.impl.javadoc;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.runner.ExecutionEnvironmentBuilder;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.execution.util.ExecutionErrorDialog;
import consulo.language.editor.scope.AnalysisScope;
import consulo.platform.base.localize.CommonLocalize;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.xml.serializer.XmlSerializer;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
@State(name = "JavadocGenerationManager", storages = @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/misc.xml"))
public final class JavadocGenerationManager implements PersistentStateComponent<Element> {
  private final JavadocConfiguration myConfiguration = new JavadocConfiguration();
  private final Project myProject;

  public static JavadocGenerationManager getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, JavadocGenerationManager.class);
  }

  @Inject
  JavadocGenerationManager(Project project) {
    myProject = project;
  }

  @Override
  public Element getState() {
    return XmlSerializer.serialize(myConfiguration, JavadocConfiguration.FILTER);
  }

  @Override
  public void loadState(Element state) {
    XmlSerializer.deserializeInto(myConfiguration, state);
  }

  @Nonnull
  public JavadocConfiguration getConfiguration() {
    return myConfiguration;
  }

  public void generateJavadoc(AnalysisScope scope) {
    try {
      JavadocGeneratorRunProfile profile = new JavadocGeneratorRunProfile(myProject, scope, myConfiguration);
      ExecutionEnvironmentBuilder.create(myProject, DefaultRunExecutor.getRunExecutorInstance(), profile).buildAndExecute();
    }
    catch (ExecutionException e) {
      ExecutionErrorDialog.show(e, CommonLocalize.titleError().get(), myProject);
    }
  }
}