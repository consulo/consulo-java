/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.psi.compiled.ClassFileDecompilers;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiManager;
import consulo.project.ProjectManager;
import consulo.virtualFileSystem.BinaryFileDecompiler;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;

import javax.annotation.Nonnull;

/**
 * @author max
 */
@ExtensionImpl
public class ClassFileDecompiler implements BinaryFileDecompiler {
  @Nonnull
  @Override
  public FileType getFileType() {
    return JavaClassFileType.INSTANCE;
  }

  @Override
  @Nonnull
  public CharSequence decompile(@Nonnull VirtualFile file) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
    if (decompiler instanceof ClassFileDecompilers.Full) {
      PsiManager manager = PsiManager.getInstance(ProjectManager.getInstance().getDefaultProject());
      return ((ClassFileDecompilers.Full) decompiler).createFileViewProvider(file, manager, true).getContents();
    }

    return decompileText(file);
  }

  @Nonnull
  public static CharSequence decompileText(@Nonnull VirtualFile file) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
    if (decompiler instanceof ClassFileDecompilers.Light) {
      return ((ClassFileDecompilers.Light) decompiler).getText(file);
    }

    return ClsFileImpl.decompile(file);
  }
}