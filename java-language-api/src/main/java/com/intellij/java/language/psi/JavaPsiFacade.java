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
package com.intellij.java.language.psi;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.dataholder.NotNullLazyKey;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

/**
 * @author max
 */
@ServiceAPI(ComponentScope.PROJECT)
public abstract class JavaPsiFacade {
  private static final NotNullLazyKey<JavaPsiFacade, Project> INSTANCE_KEY = ServiceManager.createLazyKey(JavaPsiFacade.class);

  public static JavaPsiFacade getInstance(Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  public static PsiElementFactory getElementFactory(@Nonnull Project project) {
    return getInstance(project).getElementFactory();
  }

  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope         the scope to search.
   * @return the PSI class, or null if no class with such name is found.
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
   */
  @Nonnull
  public abstract PsiClass[] findClasses(@NonNls @Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope);

  /**
   * Searches the project for the package with the specified full-qualified name and returns one
   * if it is found.
   *
   * @param qualifiedName the full-qualified name of the package to find.
   * @return the PSI package, or null if no package with such name is found.
   */
  @Nullable
  public abstract PsiJavaPackage findPackage(@Nonnull String qualifiedName);

  /**
   * Search java module for file. Can be automatic module or explicit declaration from java file
   */
  @Nullable
  public abstract PsiJavaModule findModule(@Nonnull VirtualFile file);

  @Nonnull
  public abstract PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope);

  /**
   * Returns the element factory for the project, which can be used to
   * create instances of Java and XML PSI elements.
   *
   * @return the element factory instance.
   */
  @Nonnull
  public abstract PsiElementFactory getElementFactory();

  /**
   * Returns the factory for the project, which can be used to create instances of certain java constructs from their textual
   * representation. Elements created shall not be used to later intermix (like insert into) a PSI parsed from the user codebase
   * since no formatting to the user code style will be performed in this case. Please use {@link #getElementFactory()} instead, which
   * provides exactly same methods but ensures created instances will get properly formatted.
   *
   * @return the parser facade.
   */
  @Nonnull
  public abstract PsiJavaParserFacade getParserFacade();

  /**
   * Returns the resolve helper for the project, which can be used to resolve references
   * and check accessibility of elements.
   *
   * @return the resolve helper instance.
   */
  @Nonnull
  @Deprecated
  @DeprecationInfo(value = "Use PsiResolveHelper#getInstance(Project)")
  public abstract PsiResolveHelper getResolveHelper();

  /**
   * Returns the name helper for the project, which can be used to validate
   * and parse Java identifiers.
   *
   * @return the name helper instance.
   */
  @Nonnull
  @Deprecated
  @DeprecationInfo(value = "Use PsiNameHelper#getInstance(Project)")
  public abstract PsiNameHelper getNameHelper();

  /**
   * Returns the constant expression evaluator for the project.
   *
   * @return the evaluator instance.
   */
  @Nonnull
  public abstract PsiConstantEvaluationHelper getConstantEvaluationHelper();

  /**
   * Checks if the specified package name is part of the package prefix for
   * any of the modules in this project.
   *
   * @param packageName the package name to check.
   * @return true if it is part of the package prefix, false otherwise.
   */
  public abstract boolean isPartOfPackagePrefix(@Nonnull String packageName);

  /**
   * Checks if the specified PSI element belongs to the specified package.
   *
   * @param element  the element to check the package for.
   * @param aPackage the package to check.
   * @return true if the element belongs to the package, false otherwise.
   */
  public abstract boolean isInPackage(@Nonnull PsiElement element, @Nonnull PsiJavaPackage aPackage);

  /**
   * Checks if the specified PSI elements belong to the same package.
   *
   * @param element1 the first element to check.
   * @param element2 the second element to check.
   * @return true if the elements are in the same package, false otherwise.
   */
  public abstract boolean arePackagesTheSame(@Nonnull PsiElement element1, @Nonnull PsiElement element2);

  @Nonnull
  public abstract Project getProject();


  /**
   * Searches the scope for a unique Java module with the given name.
   */
  @Nullable
  public abstract PsiJavaModule findModule(@Nonnull String moduleName, @Nonnull GlobalSearchScope scope);

  /**
   * Searches the scope for Java modules with the given name.
   */
  @Nonnull
  public abstract Collection<PsiJavaModule> findModules(@Nonnull String moduleName, @Nonnull GlobalSearchScope scope);
}
