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
package com.intellij.java.language.psi;

import consulo.application.util.function.Processor;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Allows to extend the mechanism of locating classes and packages by full-qualified name.
 * Implementations of this interface need to be registered as extensions in order
 * to be picked up by {@link JavaPsiFacade}.
 */
public abstract class PsiElementFinder {
  public static final ExtensionPointName<PsiElementFinder> EP_NAME = ExtensionPointName.create("consulo.java.elementFinder");

  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope         the scope to search.
   * @return the PSI class, or null if no class with such name is found.
   * @see JavaPsiFacade#findClass(String, GlobalSearchScope)
   */
  @Nullable
  public abstract PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope);

  /**
   * Searches the specified scope within the project for classes with the specified full-qualified
   * name and returns all found classes.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope         the scope to search.
   * @return the array of found classes, or an empty array if no classes are found.
   * @see JavaPsiFacade#findClasses(String, GlobalSearchScope)
   */
  @Nonnull
  public abstract PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope);

  /**
   * Searches the project for the package with the specified full-qualified name and returns one
   * if it is found.
   *
   * @param qualifiedName the full-qualified name of the package to find.
   * @return the PSI package, or null if no package with such name is found.
   * @see JavaPsiFacade#findPackage(String)
   */
  @Nullable
  public PsiJavaPackage findPackage(@Nonnull String qualifiedName) {
    return null;
  }

  /**
   * Returns the list of subpackages of the specified package in the specified search scope.
   *
   * @param psiPackage the package to return the list of subpackages for.
   * @param scope      the scope in which subpackages are searched.
   * @return the list of subpackages.
   * @see PsiJavaPackage#getSubPackages(GlobalSearchScope)
   */
  @Nonnull
  public PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
    return PsiJavaPackage.EMPTY_ARRAY;
  }

  /**
   * Returns the list of classes in the specified package and in the specified search scope.
   *
   * @param psiPackage the package to return the list of classes in.
   * @param scope      the scope in which classes are searched.
   * @return the list of classes.
   * @see PsiJavaPackage#getClasses(GlobalSearchScope)
   */
  @Nonnull
  public PsiClass[] getClasses(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  /**
   * A method to optimize resolve (to only search classes in a package which might be there)
   */
  @Nonnull
  public Set<String> getClassNames(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
    return getClassNames(getClasses(psiPackage, scope));
  }

  @Nonnull
  protected static Set<String> getClassNames(@Nonnull PsiClass[] classes) {
    if (classes.length == 0) {
      return Collections.emptySet();
    }

    final HashSet<String> names = new HashSet<String>();
    for (PsiClass aClass : classes) {
      ContainerUtil.addIfNotNull(names, aClass.getName());
    }
    return names;
  }

  public boolean processPackageDirectories(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope,
                                           @Nonnull Processor<PsiDirectory> consumer) {
    return processPackageDirectories(psiPackage, scope, consumer, false);
  }

  public boolean processPackageDirectories(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope,
                                           @Nonnull Processor<PsiDirectory> consumer, boolean includeLibrarySources) {
    return true;
  }

  /**
   * Returns the list of classes in the specified package and in the specified search scope.
   *
   * @param className  short name of the class
   * @param psiPackage the package to return the list of classes in.
   * @param scope      the scope in which classes are searched.
   * @return the list of classes.
   * @see PsiJavaPackage#getClasses(GlobalSearchScope)
   */
  @Nonnull
  public PsiClass[] getClasses(@Nullable String className, @Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
    PsiClass[] allClasses = getClasses(psiPackage, scope);
    if (className == null) {
      return allClasses;
    }
    return filterByName(className, allClasses);
  }

  @Nonnull
  public static PsiClass[] filterByName(@Nonnull String className, @Nonnull PsiClass[] classes) {
    if (classes.length == 0) {
      return PsiClass.EMPTY_ARRAY;
    }
    if (classes.length == 1) {
      return className.equals(classes[0].getName()) ? classes : PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> foundClasses = new SmartList<PsiClass>();
    for (PsiClass psiClass : classes) {
      if (className.equals(psiClass.getName())) {
        foundClasses.add(psiClass);
      }
    }
    return foundClasses.isEmpty() ? PsiClass.EMPTY_ARRAY : foundClasses.toArray(new PsiClass[foundClasses.size()]);
  }

}
