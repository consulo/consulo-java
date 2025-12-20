/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.file;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.file.FileTypeManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDirectoryMethodProxy;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.virtualFileSystem.fileType.FileType;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaPsiDirectoryMethodProxy implements PsiDirectoryMethodProxy {
  private static final Logger LOG = Logger.getInstance(JavaPsiDirectoryMethodProxy.class);


  @Override
  public boolean checkCreateFile(@Nonnull PsiDirectory psiDirectory, @Nonnull String name) throws IncorrectOperationException {
    FileType type = FileTypeManager.getInstance().getFileTypeByFileName(name);
    if (type == JavaClassFileType.INSTANCE) {
      throw new IncorrectOperationException("Cannot create class-file");
    }

    return true;
  }

  @Override
  public PsiElement add(@Nonnull PsiDirectory psiDirectory, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      String name = ((PsiClass) element).getName();
      if (name != null) {
        PsiClass newClass = JavaDirectoryService.getInstance().createClass(psiDirectory, name);
        return newClass.replace(element);
      } else {
        LOG.error("not implemented");
        return null;
      }
    }

    return null;
  }

  @Override
  public boolean checkAdd(@Nonnull PsiDirectory psiDirectory, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      if (((PsiClass) element).getContainingClass() == null) {
        JavaDirectoryServiceImpl.checkCreateClassOrInterface(psiDirectory, ((PsiClass) element).getName());
      } else {
        LOG.error("not implemented");
      }
    }
    return true;
  }
}
