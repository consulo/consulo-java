// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.indexing.impl.stubs.index;

import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

class JavaAutoModuleFilterScope extends DelegatingGlobalSearchScope {
  JavaAutoModuleFilterScope(@Nonnull GlobalSearchScope baseScope) {
    super(baseScope);
  }

  @Override
  public boolean contains(@Nonnull VirtualFile file) {
    if (!super.contains(file)) {
      return false;
    }

    VirtualFile root = file;
    if (!file.isDirectory()) {
      root = file.getParent().getParent();
      Project project = getProject();
      if (project == null || !root.equals(ProjectFileIndex.getInstance(project).getSourceRootForFile(file))) {
        return false;
      }
    }
    if (JavaModuleNameIndex.descriptorFile(root) != null) {
      return false;
    }

    return true;
  }
}