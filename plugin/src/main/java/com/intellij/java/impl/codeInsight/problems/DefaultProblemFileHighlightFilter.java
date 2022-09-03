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
package com.intellij.java.impl.codeInsight.problems;

import jakarta.inject.Inject;

import consulo.compiler.CompilerManager;
import consulo.project.Project;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import consulo.java.impl.util.JavaProjectRootsUtil;

/**
* @author yole
*/
public class DefaultProblemFileHighlightFilter implements Condition<VirtualFile> {
  private final Project myProject;

  @Inject
  public DefaultProblemFileHighlightFilter(Project project) {
    myProject = project;
  }

  @Override
  public boolean value(final VirtualFile file) {
    return JavaProjectRootsUtil.isJavaSourceFile(myProject, file, false)
      && !CompilerManager.getInstance(myProject).isExcludedFromCompilation(file);
  }
}
