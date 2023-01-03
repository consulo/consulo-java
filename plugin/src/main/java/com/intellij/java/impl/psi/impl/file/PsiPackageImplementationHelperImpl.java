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
package com.intellij.java.impl.psi.impl.file;

import com.intellij.java.language.impl.psi.NonClasspathClassFinder;
import com.intellij.java.language.impl.psi.impl.file.PsiPackageImplementationHelper;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ServiceImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.ui.view.ProjectView;
import consulo.project.ui.wm.ToolWindowId;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Singleton;

/**
 * @author yole
 */
@Singleton
@ServiceImpl
public class PsiPackageImplementationHelperImpl extends PsiPackageImplementationHelper {
  @Override
  public GlobalSearchScope adjustAllScope(PsiJavaPackage psiPackage, GlobalSearchScope globalSearchScope) {
    return NonClasspathClassFinder.addNonClasspathScope(psiPackage.getProject(), globalSearchScope);
  }

  @Override
  public VirtualFile[] occursInPackagePrefixes(PsiJavaPackage psiPackage) {
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public void handleQualifiedNameChange(final PsiJavaPackage psiPackage, final String newQualifiedName) {

  }

  @Override
  public void navigate(final PsiJavaPackage psiPackage, final boolean requestFocus) {
    final Project project = psiPackage.getProject();
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
    window.activate(() -> {
      final ProjectView projectView = ProjectView.getInstance(project);
      PsiDirectory[] directories = suggestMostAppropriateDirectories(psiPackage);
      if (directories.length == 0) {
        return;
      }
      projectView.select(directories[0], directories[0].getVirtualFile(), requestFocus);
    });
  }

  private static PsiDirectory[] suggestMostAppropriateDirectories(PsiJavaPackage psiPackage) {
    final Project project = psiPackage.getProject();
    PsiDirectory[] directories = null;
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      final Document document = editor.getDocument();
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        final Module module = ModuleUtil.findModuleForPsiElement(psiFile);
        if (module != null) {
          directories = psiPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesScope(module));
        }
        else {
          directories = psiPackage.getDirectories(GlobalSearchScope.notScope(GlobalSearchScope.projectScope(project)));
        }
      }
    }

    if (directories == null || directories.length == 0) {
      directories = psiPackage.getDirectories();
    }
    return directories;
  }

  @Override
  public boolean packagePrefixExists(PsiJavaPackage psiPackage) {
    return false;
  }

  @Override
  public Object[] getDirectoryCachedValueDependencies(PsiJavaPackage psiPackage) {
    return new Object[]{
      PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT,
      ProjectRootManager.getInstance(psiPackage.getProject())
    };
  }
}
