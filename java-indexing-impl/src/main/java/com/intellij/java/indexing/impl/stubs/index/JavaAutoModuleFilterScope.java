// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing.impl.stubs.index;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.util.JavaMultiReleaseUtil;
import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

class JavaAutoModuleFilterScope extends DelegatingGlobalSearchScope {
  JavaAutoModuleFilterScope(GlobalSearchScope baseScope) {
    super(baseScope);
  }

  @Override
  public boolean contains(VirtualFile file) {
    if (!super.contains(file)) {
      return false;
    }

    VirtualFile root = file;
    if (!file.isDirectory()) {
      root = file.getParent().getParent();
      if (root == null) {
        return false;
      }
      Project project = getProject();
      if (project == null) {
        return false;
      }
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      if (!root.equals(fileIndex.getSourceRootForFile(file)) && !root.equals(fileIndex.getClassRootForFile(file))) {
        return false;
      }
    }

    if (JavaMultiReleaseUtil.findVersionSpecificFile(root, PsiJavaModule.MODULE_INFO_CLS_FILE, LanguageLevel.HIGHEST) != null) {
      return false;
    }

    return true;
  }
}
