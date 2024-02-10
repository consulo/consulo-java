/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.ide.highlighter;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.fileEditor.structureView.StructureViewBuilder;
import consulo.fileEditor.structureView.StructureViewBuilderProvider;
import consulo.language.editor.structureView.PsiStructureViewFactory;
import consulo.language.psi.PsiCompiledFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 */
@ExtensionImpl
public class JavaClsStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Override
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@Nonnull FileType fileType, @Nonnull VirtualFile file, @Nonnull Project project) {
    if (fileType == JavaClassFileType.INSTANCE) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

      if (psiFile instanceof PsiCompiledFile) {
        psiFile = ((PsiCompiledFile) psiFile).getDecompiledPsiFile();
      }

      if (psiFile != null) {
        PsiStructureViewFactory factory = PsiStructureViewFactory.forLanguage(psiFile.getLanguage());
        if (factory != null) {
          return factory.getStructureViewBuilder(psiFile);
        }
      }
    }

    return null;
  }
}