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
package com.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayFactory;
import consulo.psi.PsiPackage;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a Java package.
 */
public interface PsiJavaPackage extends PsiJvmModifiersOwner, PsiPackage
{
  @NonNls String PACKAGE_INFO_CLASS = "package-info";
  @NonNls String PACKAGE_INFO_FILE = PACKAGE_INFO_CLASS + ".java";

  PsiJavaPackage[] EMPTY_ARRAY = new PsiJavaPackage[0];

  ArrayFactory<PsiJavaPackage> ARRAY_FACTORY = new ArrayFactory<PsiJavaPackage>() {
    @Nonnull
    @Override
    public PsiJavaPackage[] create(final int count) {
      return count == 0 ? EMPTY_ARRAY : new PsiJavaPackage[count];
    }
  };

  @Nullable
  PsiJavaPackage getParentPackage();

  /**
   * Returns the list of subpackages of this package under all source roots of the project.
   *
   * @return the array of subpackages.
   */
  @Nonnull
  PsiJavaPackage[] getSubPackages();

  /**
   * Returns the list of subpackages of this package in the specified search scope.
   *
   * @param scope the scope in which packages are searched.
   * @return the array of subpackages.
   */
  @Nonnull
  PsiJavaPackage[] getSubPackages(@Nonnull GlobalSearchScope scope);

  /**
   * Returns the list of classes in all directories corresponding to the package.
   *
   * @return the array of classes.
   */
  @Nonnull
  PsiClass[] getClasses();

  /**
   * Returns the list of classes in directories corresponding to the package in the specified
   * search scope.
   *
   * @param scope the scope in which directories are searched.
   * @return the array of classes.
   */
  @Nonnull
  PsiClass[] getClasses(@Nonnull GlobalSearchScope scope);

  /**
   * Returns the list of package-level annotations for the package.
   *
   * @return the list of annotations, or null if the package does not have any package-level annotations.
   * @since 5.1
   */
  @Nullable
  PsiModifierList getAnnotationList();

  /**
   * Returns source roots that this package occurs in package prefixes of.
   *
   * @return the array of virtual files for the source roots.
   */
  VirtualFile[] occursInPackagePrefixes();

  boolean containsClassNamed(String name);

  @Nonnull
  PsiClass[] findClassByShortName(@Nonnull String name, @Nonnull GlobalSearchScope scope);
}
