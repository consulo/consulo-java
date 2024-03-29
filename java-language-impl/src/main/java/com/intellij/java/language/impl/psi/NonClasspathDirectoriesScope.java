/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.java.language.impl.psi;

import consulo.annotation.UsedInPlugin;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class NonClasspathDirectoriesScope extends GlobalSearchScope {
  private final Set<VirtualFile> myRoots;

  public NonClasspathDirectoriesScope(@Nonnull Collection<VirtualFile> roots) {
    myRoots = new HashSet<>(roots);
  }

  @Override
  public boolean isSearchOutsideRootModel() {
    return true;
  }

  @Nonnull
  @UsedInPlugin
  public static GlobalSearchScope compose(@Nonnull List<VirtualFile> roots) {
    if (roots.isEmpty()) {
      return EMPTY_SCOPE;
    }

    return new NonClasspathDirectoriesScope(roots);
  }

  @Override
  public boolean contains(@Nonnull VirtualFile file) {
    return VirtualFileUtil.isUnder(file, myRoots);
  }

  @Override
  public int compare(@Nonnull VirtualFile file1, @Nonnull VirtualFile file2) {
    return 0;
  }

  @Override
  public boolean isSearchInModuleContent(@Nonnull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NonClasspathDirectoriesScope)) return false;

    NonClasspathDirectoriesScope that = (NonClasspathDirectoriesScope) o;

    if (!myRoots.equals(that.myRoots)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myRoots.hashCode();
    return result;
  }

  @Nonnull
  @Override
  public String getDisplayName() {
    if (myRoots.size() == 1) {
      VirtualFile root = myRoots.iterator().next();
      return "Directory '" + root.getName() + "'";
    }
    return "Directories " + StringUtil.join(myRoots, file -> "'" + file.getName() + "'", ", ");
  }
}
