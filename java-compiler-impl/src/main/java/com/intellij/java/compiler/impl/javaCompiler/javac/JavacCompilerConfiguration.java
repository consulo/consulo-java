/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.compiler.impl.javaCompiler.javac;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.project.macro.ProjectPathMacroManager;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;

@Singleton
@State(
  name = "JavacCompilerConfiguration",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/compiler.xml")
  }
)
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class JavacCompilerConfiguration implements PersistentStateComponent<JpsJavaCompilerOptions> {
  @Nonnull
  public static JpsJavaCompilerOptions getInstance(Project project) {
    final JavacCompilerConfiguration service = ServiceManager.getService(project, JavacCompilerConfiguration.class);
    return service.mySettings;
  }

  private final JpsJavaCompilerOptions mySettings = new JpsJavaCompilerOptions();
  private final Project myProject;

  @Inject
  public JavacCompilerConfiguration(Project project) {
    myProject = project;
  }

  @Override
  @Nonnull
  public JpsJavaCompilerOptions getState() {
    JpsJavaCompilerOptions state = new JpsJavaCompilerOptions();
    XmlSerializerUtil.copyBean(mySettings, state);
    state.ADDITIONAL_OPTIONS_STRING =
      ProjectPathMacroManager.getInstance(myProject).collapsePathsRecursively(state.ADDITIONAL_OPTIONS_STRING);
    return state;
  }

  @Override
  public void loadState(JpsJavaCompilerOptions state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }
}