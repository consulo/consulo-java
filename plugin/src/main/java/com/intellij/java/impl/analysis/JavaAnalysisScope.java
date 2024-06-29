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

/*
 * User: anna
 * Date: 14-Jan-2008
 */
package com.intellij.java.impl.analysis;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.PackageScope;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.scope.localize.AnalysisScopeLocalize;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

public class JavaAnalysisScope extends AnalysisScope {
  public static final int PACKAGE = 5;

  public JavaAnalysisScope(PsiPackage pack, Module module) {
    super(pack.getProject());
    myModule = module;
    myElement = pack;
    myType = PACKAGE;
  }

  public JavaAnalysisScope(final PsiJavaFile psiFile) {
    super(psiFile);
  }

  @Override
  @Nonnull
  public AnalysisScope getNarrowedComplementaryScope(@Nonnull Project defaultProject) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(defaultProject).getFileIndex();
    final HashSet<Module> modules = new HashSet<>();
    if (myType == FILE) {
      if (myElement instanceof PsiJavaFile/* && !FileTypeUtils.isInServerPageFile(myElement)*/) {
        PsiJavaFile psiJavaFile = (PsiJavaFile) myElement;
        final PsiClass[] classes = psiJavaFile.getClasses();
        boolean onlyPackLocalClasses = true;
        for (final PsiClass aClass : classes) {
          if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            onlyPackLocalClasses = false;
          }
        }
        if (onlyPackLocalClasses) {
          final PsiDirectory psiDirectory = psiJavaFile.getContainingDirectory();
          if (psiDirectory != null) {
            return new JavaAnalysisScope(JavaDirectoryService.getInstance().getPackage(psiDirectory),
                null);
          }
        }
      }
    } else if (myType == PACKAGE) {
      final PsiDirectory[] directories = ((PsiPackage) myElement).getDirectories();
      for (PsiDirectory directory : directories) {
        modules.addAll(getAllInterestingModules(fileIndex, directory.getVirtualFile()));
      }
      return collectScopes(defaultProject, modules);
    }
    return super.getNarrowedComplementaryScope(defaultProject);         
  }


  @Nonnull
  @Override
  public String getShortenName() {
    return myType == PACKAGE
      ? AnalysisScopeLocalize.scopePackage(((PsiPackage)myElement).getQualifiedName()).get()
      : super.getShortenName();
  }

  @Nonnull
  @Override
  public String getDisplayName() {
    return myType == PACKAGE
      ? AnalysisScopeLocalize.scopePackage(((PsiPackage)myElement).getQualifiedName()).get()
      : super.getDisplayName();
  }

  @Override
  protected void initFilesSet() {
    if (myType == PACKAGE) {
      myFilesSet = new HashSet<>();
      accept(createFileSearcher());
      return;
    }
    super.initFilesSet();
  }

  @Override
  public boolean accept(@Nonnull Processor<VirtualFile> processor) {
    if (myElement instanceof PsiJavaPackage pack) {
      final Set<PsiDirectory> dirs = new HashSet<>();
      pack.getApplication().runReadAction(() -> {
        ContainerUtil.addAll(dirs, pack.getDirectories(GlobalSearchScope.projectScope(myElement.getProject())));
      });
      for (PsiDirectory dir : dirs) {
        if (!accept(dir, processor)) {
          return false;
        }
      }
      return true;
    }
    return super.accept(processor);
  }

  @Nonnull
  @Override
  public SearchScope toSearchScope() {
    if (myType == PACKAGE) {
      return new PackageScope((PsiJavaPackage) myElement, true, true);
    }
    return super.toSearchScope();
  }
}
