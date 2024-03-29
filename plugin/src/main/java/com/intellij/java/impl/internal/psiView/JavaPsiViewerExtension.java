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
package com.intellij.java.impl.internal.psiView;

import consulo.ide.impl.idea.internal.psiView.PsiViewerExtension;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JavaPsiViewerExtension implements PsiViewerExtension {
  @Nonnull
  public FileType getDefaultFileType() {
    return JavaFileType.INSTANCE;
  }

  protected static PsiElementFactory getFactory(Project project) {
    return JavaPsiFacade.getElementFactory(project);
  }
}
