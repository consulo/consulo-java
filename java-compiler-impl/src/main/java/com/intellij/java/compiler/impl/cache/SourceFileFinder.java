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
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
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
  private Map<Path, String> myProjectSourceRoots = null;
  private final CompilerManager myCompilerConfiguration;

  public SourceFileFinder(Project project, CompileContext compileContext) {
    myProject = project;
    myCompileContext = compileContext;
    myCompilerConfiguration = CompilerManager.getInstance(project);
  }

  @Nullable
  public Path findSourceFile(String qualifiedName, final String srcName, boolean checkIfExcludedFromMake) {
    // optimization
    final int dollar = qualifiedName.indexOf('$');
    final String outerQName = (dollar >= 0)? qualifiedName.substring(0, dollar) : qualifiedName;
    final PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(outerQName, GlobalSearchScope.projectScope(myProject));
    for (PsiClass aClass : classes) {
      final PsiFile file = aClass.getContainingFile();
      if (srcName.equals(file.getName())) {
        final VirtualFile vFile = file.getVirtualFile();
        if (vFile != null && vFile.isInLocalFileSystem()) {
          Path path = vFile.toNioPath();
          if (!checkIfExcludedFromMake || !myCompilerConfiguration.isExcludedFromCompilation(path)) {
            return path;
          }
        }
      }
    }

    String relativePath = JavaMakeUtil.createRelativePathToSource(qualifiedName, srcName);
    Map<Path, String> dirs = getAllSourceRoots();
    if (!StringUtil.startsWithChar(relativePath, '/')) {
      relativePath = "/" + relativePath;
    }
    for (final Map.Entry<Path, String> entry : dirs.entrySet()) {
      final Path root = entry.getKey();
      final String prefix = entry.getValue();
      String path;
      if (prefix.length() > 0) {
        if (FileUtil.startsWith(relativePath, prefix)) {
          // if there is package prefix assigned to the root, the relative path should be corrected
          path = FileUtil.toSystemIndependentName(root.toString()) + relativePath.substring(prefix.length() - 1);
        }
        else {
          // if there is package prefix, but the relative path does not match it, skip the root
          continue;
        }
      }
      else {
        path = FileUtil.toSystemIndependentName(root.toString()) + relativePath;
      }
      Path file = Path.of(FileUtil.toSystemDependentName(path));
      if (Files.exists(file) && (!checkIfExcludedFromMake || !myCompilerConfiguration.isExcludedFromCompilation(root))) {
        return file;
      }
    }
    return null;
  }

  private Map<Path, String> getAllSourceRoots() {
    if (myProjectSourceRoots == null) {
      myProjectSourceRoots = new HashMap<Path, String>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          final LocalFileSystem fs = LocalFileSystem.getInstance();
          final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
          for (Module allModule : allModules) {
            final Path[] sourceRoots = myCompileContext.getSourceRoots(allModule);
            for (final Path sourceRoot : sourceRoots) {
              VirtualFile rootFile = fs.findFileByNioFile(sourceRoot);
              String packageName = rootFile != null ? fileIndex.getPackageNameByDirectory(rootFile) : null;
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
