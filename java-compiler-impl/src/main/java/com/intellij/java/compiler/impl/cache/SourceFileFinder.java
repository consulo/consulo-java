/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.compiler.impl.cache;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.ApplicationManager;
import consulo.compiler.CompileContext;
import consulo.compiler.CompilerManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Assumes that source roots in the project has not changed and caches the snapshot of source roots for effective searching
 * User: JEKA
 * Date: Jul 17, 2003
 * Time: 9:52:26 PM
 */
public class SourceFileFinder {
  private final Project myProject;
  private final CompileContext myCompileContext;
  private Map<VirtualFile, String> myProjectSourceRoots = null;
  private final CompilerManager myCompilerConfiguration;

  public SourceFileFinder(Project project, CompileContext compileContext) {
    myProject = project;
    myCompileContext = compileContext;
    myCompilerConfiguration = CompilerManager.getInstance(project);
  }

  public VirtualFile findSourceFile(String qualifiedName, final String srcName, boolean checkIfExcludedFromMake) {
    // optimization
    final int dollar = qualifiedName.indexOf('$');
    final String outerQName = (dollar >= 0)? qualifiedName.substring(0, dollar) : qualifiedName;
    final PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(outerQName, GlobalSearchScope.projectScope(myProject));
    for (PsiClass aClass : classes) {
      final PsiFile file = aClass.getContainingFile();
      if (srcName.equals(file.getName())) {
        final VirtualFile vFile = file.getVirtualFile();
        if (vFile != null && (!checkIfExcludedFromMake || !myCompilerConfiguration.isExcludedFromCompilation(vFile))) {
          return vFile;
        }
      }
    }

    String relativePath = JavaMakeUtil.createRelativePathToSource(qualifiedName, srcName);
    Map<VirtualFile, String> dirs = getAllSourceRoots();
    if (!StringUtil.startsWithChar(relativePath, '/')) {
      relativePath = "/" + relativePath;
    }
    LocalFileSystem fs = LocalFileSystem.getInstance();
    for (final VirtualFile virtualFile : dirs.keySet()) {
      final String prefix = dirs.get(virtualFile);
      String path;
      if (prefix.length() > 0) {
        if (FileUtil.startsWith(relativePath, prefix)) {
          // if there is package prefix assigned to the root, the relative path should be corrected
          path = virtualFile.getPath() + relativePath.substring(prefix.length() - 1);
        }
        else {
          // if there is package prefix, but the relative path does not match it, skip the root
          continue;
        }
      }
      else {
        path = virtualFile.getPath() + relativePath;
      }
      VirtualFile file = fs.findFileByPath(path);
      if (file != null && (!checkIfExcludedFromMake || !myCompilerConfiguration.isExcludedFromCompilation(virtualFile))) {
        return file;
      }
    }
    return null;
  }

  private Map<VirtualFile, String> getAllSourceRoots() {
    if (myProjectSourceRoots == null) {
      myProjectSourceRoots = new HashMap<VirtualFile, String>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
          for (Module allModule : allModules) {
            final VirtualFile[] sourceRoots = myCompileContext.getSourceRoots(allModule);
            for (final VirtualFile sourceRoot : sourceRoots) {
              String packageName = fileIndex.getPackageNameByDirectory(sourceRoot);
              myProjectSourceRoots
                .put(sourceRoot, packageName == null || packageName.length() == 0 ? "" : "/" + packageName.replace('.', '/') + "/");
            }
          }
        }
      });
    }
    return myProjectSourceRoots;
  }

}
