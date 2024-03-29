/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.util;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FindClassUtil {
  /**
   * Searches the project for modules that contain the class with the specified full-qualified name within
   * the module dependencies or libraries.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @return the modules that contain the given class in dependencies or libraries.
   */
  @Nonnull
  public static Collection<Module> findModulesWithClass(@Nonnull Project project, @NonNls @Nonnull String qualifiedName) {
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass[] possibleClasses = facade.findClasses(qualifiedName, allScope);
    if (possibleClasses.length == 0) {
      return Collections.emptyList();
    }
    Set<Module> relevantModules = new LinkedHashSet<>();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiClass aClass : possibleClasses) {
      VirtualFile classFile = aClass.getContainingFile().getVirtualFile();
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(classFile)) {
        relevantModules.add(orderEntry.getOwnerModule());
      }
    }
    return relevantModules;
  }
}
