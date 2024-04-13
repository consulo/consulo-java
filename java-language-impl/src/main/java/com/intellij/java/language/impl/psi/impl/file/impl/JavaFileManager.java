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
package com.intellij.java.language.impl.psi.impl.file.impl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

@ServiceAPI(ComponentScope.PROJECT)
public interface JavaFileManager {
  static JavaFileManager getInstance(@Nonnull Project project) {
    return project.getInstance(JavaFileManager.class);
  }

  @Nullable
  PsiClass findClass(@Nonnull String qName, @Nonnull GlobalSearchScope scope);

  @Nullable
  PsiJavaPackage findPackage(@Nonnull String qualifiedName);

  PsiClass[] findClasses(@Nonnull String qName, @Nonnull GlobalSearchScope scope);

  Collection<String> getNonTrivialPackagePrefixes();

  @Nonnull
  Collection<PsiJavaModule> findModules(@Nonnull String moduleName, @Nonnull GlobalSearchScope scope);
}