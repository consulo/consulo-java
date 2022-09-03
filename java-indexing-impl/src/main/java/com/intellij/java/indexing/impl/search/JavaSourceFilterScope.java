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
package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author max
 */
public class JavaSourceFilterScope extends DelegatingGlobalSearchScope {
  @Nullable
  private final ProjectFileIndex myIndex;
  private final boolean myIncludeVersions;

  public JavaSourceFilterScope(@Nonnull ProjectAwareSearchScope delegate) {
    this((GlobalSearchScope) delegate, false);
  }

  /**
   * By default, the scope excludes version-specific classes of multi-release .jar files
   * (i.e. *.class files located under META-INF/versions/ directory).
   * Setting {@code includeVersions} parameter to {@code true} allows such files to pass the filter.
   */
  public JavaSourceFilterScope(@Nonnull GlobalSearchScope delegate, boolean includeVersions) {
    super(delegate);
    Project project = getProject();
    myIndex = project == null ? null : ProjectRootManager.getInstance(project).getFileIndex();
    myIncludeVersions = includeVersions;
  }

  @Override
  public boolean contains(@Nonnull VirtualFile file) {
    if (!super.contains(file)) {
      return false;
    }

    if (myIndex == null) {
      return false;
    }

    if (file.getFileType() == JavaClassFileType.INSTANCE) {
      return myIndex.isInLibraryClasses(file) && (myIncludeVersions || !isVersioned(file, myIndex));
    }

    return myIndex.isInSourceContent(file) ||
        myBaseScope.isForceSearchingInLibrarySources() && myIndex.isInLibrarySource(file);
  }

  private static boolean isVersioned(VirtualFile file, ProjectFileIndex index) {
    VirtualFile root = index.getClassRootForFile(file);
    while ((file = file.getParent()) != null && !file.equals(root)) {
      if (Comparing.equal(file.getNameSequence(), "versions")) {
        VirtualFile parent = file.getParent();
        if (parent != null && Comparing.equal(parent.getNameSequence(), "META-INF")) {
          return true;
        }
      }
    }

    return false;
  }
}