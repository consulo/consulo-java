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
package com.intellij.java.impl.openapi.roots;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.content.library.Library;
import consulo.util.concurrent.AsyncResult;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * Provides methods to perform high-level modifications of project configuration accordingly with dependency management system used in the
 * project. E.g. if the project is imported from Maven the methods will modify pom.xml files and invoke reimporting to update IDEA's
 * project model. Since importing the changes to IDEA's project model may take a while the method work asynchronously and returns
 * {@link AsyncResult} objects which may be used to be notified when the project configuration is finally updated.
 *
 * @author nik
 * @see JavaProjectModelModifier
 */
@ServiceAPI(ComponentScope.PROJECT)
public abstract class JavaProjectModelModificationService {
  public static JavaProjectModelModificationService getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, JavaProjectModelModificationService.class);
  }

  public AsyncResult<Void> addDependency(@Nonnull Module from, @Nonnull Module to) {
    return addDependency(from, to, DependencyScope.COMPILE);
  }

  public abstract AsyncResult<Void> addDependency(@Nonnull Module from, @Nonnull Module to, @Nonnull DependencyScope scope);

  public AsyncResult<Void> addDependency(@Nonnull Module from, @Nonnull ExternalLibraryDescriptor libraryDescriptor) {
    return addDependency(from, libraryDescriptor, DependencyScope.COMPILE);
  }

  public AsyncResult<Void> addDependency(@Nonnull Module from, @Nonnull ExternalLibraryDescriptor descriptor, @Nonnull DependencyScope scope) {
    return addDependency(Collections.singletonList(from), descriptor, scope);
  }

  public abstract AsyncResult<Void> addDependency(@Nonnull Collection<Module> from, @Nonnull ExternalLibraryDescriptor libraryDescriptor, @Nonnull DependencyScope scope);

  public abstract AsyncResult<Void> addDependency(@Nonnull Module from, @Nonnull Library library, @Nonnull DependencyScope scope);

  public abstract AsyncResult<Void> changeLanguageLevel(@Nonnull Module module, @Nonnull LanguageLevel languageLevel);
}
