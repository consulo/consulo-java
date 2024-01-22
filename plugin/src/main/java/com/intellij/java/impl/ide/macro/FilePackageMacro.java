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
package com.intellij.java.impl.ide.macro;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.IdeBundle;
import consulo.pathMacro.Macro;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.dataContext.DataContext;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nullable;

@ExtensionImpl
public final class FilePackageMacro extends Macro {
  public String getName() {
    return "FilePackage";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.package");
  }

  public String expand(DataContext dataContext) {
    PsiJavaPackage aPackage = getFilePackage(dataContext);
    if (aPackage == null) return null;
    return aPackage.getName();
  }

  @Nullable
  static PsiJavaPackage getFilePackage(DataContext dataContext) {
    PsiFile psiFile = dataContext.getData(LangDataKeys.PSI_FILE);
    if (psiFile == null) return null;
    PsiDirectory containingDirectory = psiFile.getContainingDirectory();
    if (containingDirectory == null || !containingDirectory.isValid()) return null;
    return JavaDirectoryService.getInstance().getPackage(containingDirectory);
  }
}
