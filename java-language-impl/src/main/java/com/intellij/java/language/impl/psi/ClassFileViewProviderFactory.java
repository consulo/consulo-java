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
package com.intellij.java.language.impl.psi;

import com.intellij.java.language.psi.compiled.ClassFileDecompilers;
import consulo.language.Language;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.file.FileViewProvider;
import consulo.language.file.FileViewProviderFactory;
import consulo.language.psi.PsiManager;

import javax.annotation.Nonnull;

import static com.intellij.java.language.psi.compiled.ClassFileDecompilers.Full;

/**
 * @author max
 */
public class ClassFileViewProviderFactory implements FileViewProviderFactory {
  @Nonnull
  @Override
  public FileViewProvider createFileViewProvider(@Nonnull VirtualFile file, Language language, @Nonnull PsiManager manager, boolean eventSystemEnabled) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
    if (decompiler instanceof Full) {
      return ((Full) decompiler).createFileViewProvider(file, manager, eventSystemEnabled);
    }

    return new ClassFileViewProvider(manager, file, eventSystemEnabled);
  }
}