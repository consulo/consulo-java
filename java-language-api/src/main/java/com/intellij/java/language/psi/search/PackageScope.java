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

/*
 * @author max
 */
package com.intellij.java.language.psi.search;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.DirectoryIndex;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import jakarta.annotation.Nonnull;
import java.util.Collection;

public class PackageScope extends GlobalSearchScope {
  private final Collection<VirtualFile> myDirs;
  private final PsiJavaPackage myPackage;
  private final boolean myIncludeSubpackages;
  private final boolean myIncludeLibraries;
  protected final boolean myPartOfPackagePrefix;
  protected final String myPackageQualifiedName;
  protected final String myPackageQNamePrefix;

  public PackageScope(@jakarta.annotation.Nonnull PsiJavaPackage aPackage, boolean includeSubpackages, final boolean includeLibraries) {
    super(aPackage.getProject());
    myPackage = aPackage;
    myIncludeSubpackages = includeSubpackages;

    Project project = myPackage.getProject();
    myPackageQualifiedName = myPackage.getQualifiedName();
    myDirs = DirectoryIndex.getInstance(project).getDirectoriesByPackageName(myPackageQualifiedName, true).findAll();
    myIncludeLibraries = includeLibraries;

    myPartOfPackagePrefix = JavaPsiFacade.getInstance(getProject()).isPartOfPackagePrefix(myPackageQualifiedName);
    myPackageQNamePrefix = myPackageQualifiedName + ".";
  }

  @Override
  public boolean contains(VirtualFile file) {
    for (VirtualFile scopeDir : myDirs) {
      boolean inDir = myIncludeSubpackages
          ? VirtualFileUtil.isAncestor(scopeDir, file, false)
          : Comparing.equal(file.getParent(), scopeDir);
      if (inDir) return true;
    }
    if (myPartOfPackagePrefix && myIncludeSubpackages) {
      final PsiFile psiFile = myPackage.getManager().findFile(file);
      if (psiFile instanceof PsiClassOwner) {
        final String packageName = ((PsiClassOwner) psiFile).getPackageName();
        if (myPackageQualifiedName.equals(packageName) ||
            packageName.startsWith(myPackageQNamePrefix)) return true;
      }
    }
    return false;
  }

  @Override
  public int compare(VirtualFile file1, VirtualFile file2) {
    return 0;
  }

  @Override
  public boolean isSearchInModuleContent(@Nonnull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return myIncludeLibraries;
  }

  public String toString() {
    //noinspection HardCodedStringLiteral
    return "package scope: " + myPackage +
        ", includeSubpackages = " + myIncludeSubpackages;
  }

  public static GlobalSearchScope packageScope(@Nonnull PsiJavaPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, true);
  }

  public static GlobalSearchScope packageScopeWithoutLibraries(@jakarta.annotation.Nonnull PsiJavaPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, false);
  }
}