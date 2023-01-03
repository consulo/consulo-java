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

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.CompilerManager;
import consulo.java.impl.util.JavaProjectRootsUtil;
import consulo.language.editor.wolfAnalyzer.WolfFileProblemFilter;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class DefaultProblemFileHighlightFilter implements WolfFileProblemFilter {
  private final Project myProject;

  @Inject
  public DefaultProblemFileHighlightFilter(Project project) {
    myProject = project;
  }

  @Override
  public boolean isToBeHighlighted(@Nonnull VirtualFile file) {
    return JavaProjectRootsUtil.isJavaSourceFile(myProject, file, false)
      && !CompilerManager.getInstance(myProject).isExcludedFromCompilation(file);
  }
}
