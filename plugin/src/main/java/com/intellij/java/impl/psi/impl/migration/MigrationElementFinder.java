/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.migration;

import java.util.List;

import javax.annotation.Nonnull;
import jakarta.inject.Inject;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiElementFinder;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author yole
 */
public class MigrationElementFinder extends PsiElementFinder implements DumbAware {
  private final Project myProject;

  @Inject
  public MigrationElementFinder(Project project) {
    myProject = project;
  }

  @Override
  public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      return migration.getMigrationClass(qualifiedName);
    }
    return null;
  }

  @Nonnull
  @Override
  public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      final PsiClass migrationClass = migration.getMigrationClass(qualifiedName);
      if (migrationClass != null) {
        return new PsiClass[]{migrationClass};
      }
    }
    return PsiClass.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PsiClass[] getClasses(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      List<PsiClass> classes = migration.getMigrationClasses(psiPackage.getQualifiedName());
      return classes.toArray(new PsiClass[classes.size()]);
    }
    return PsiClass.EMPTY_ARRAY;
  }
 /*
  @NotNull
  @Override
  public PsiJavaPackage[] getSubPackages(@NotNull PsiJavaPackage psiPackage, @NotNull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      List<PsiJavaPackage> packages = migration.getMigrationPackages(psiPackage.getQualifiedName());
      return packages.toArray(new PsiJavaPackage[packages.size()]);
    }
    return PsiJavaPackage.EMPTY_ARRAY;
  }     */

 /* @Override
  public PsiJavaPackage findPackage(@NotNull String qualifiedName) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      return migration.getMigrationPackage(qualifiedName);
    }
    return null;
  } */
}
