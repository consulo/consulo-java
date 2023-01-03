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
package com.intellij.java.impl.psi.impl.migration;

import com.intellij.java.language.impl.psi.impl.file.PsiPackageImpl;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiPackageManager;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;

/**
 * @author dsl
 */
public class MigrationPackageImpl extends PsiPackageImpl {
  private final PsiMigrationImpl myMigration;

  public MigrationPackageImpl(PsiMigrationImpl migration, String qualifiedName) {
    super(migration.getManager(), PsiPackageManager.getInstance(migration.getManager().getProject()), JavaModuleExtension.class, qualifiedName);
    myMigration = migration;
  }

  public String toString() {
    return "MigrationPackage: " + getQualifiedName();
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myMigration.isValid();
  }

  @Override
  public void handleQualifiedNameChange(@Nonnull String newQualifiedName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile[] occursInPackagePrefixes() {
    return VirtualFile.EMPTY_ARRAY;
  }
}
