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
package com.intellij.java.language.impl.core;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.compiled.ClsFileImpl;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.content.FileIndexFacade;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class CoreJavaDirectoryService extends JavaDirectoryService {
  private static final Logger LOG = Logger.getInstance(CoreJavaDirectoryService.class);

  @Override
  public PsiJavaPackage getPackage(@Nonnull PsiDirectory dir) {
    return null;//TODO [VISTALL] ServiceManager.getService(dir.getProject(), CoreJavaFileManager.class).getPackage(dir);
  }

  @Nonnull
  @Override
  public PsiClass[] getClasses(@Nonnull PsiDirectory dir) {
    LOG.assertTrue(dir.isValid());

    FileIndexFacade index = FileIndexFacade.getInstance(dir.getProject());
    VirtualFile virtualDir = dir.getVirtualFile();
    boolean onlyCompiled = index.isInLibraryClasses(virtualDir) && !index.isInSourceContent(virtualDir);

    List<PsiClass> classes = null;
    for (PsiFile file : dir.getFiles()) {
      if (onlyCompiled && !(file instanceof ClsFileImpl)) {
        continue;
      }
      if (file instanceof PsiClassOwner && file.getViewProvider().getLanguages().size() == 1) {
        PsiClass[] psiClasses = ((PsiClassOwner)file).getClasses();
        if (psiClasses.length == 0) continue;
        if (classes == null) classes = new ArrayList<PsiClass>();
        ContainerUtil.addAll(classes, psiClasses);
      }
    }
    return classes == null ? PsiClass.EMPTY_ARRAY : classes.toArray(new PsiClass[classes.size()]);
  }

  @Nonnull
  @Override
  public PsiClass createClass(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public PsiClass createClass(@Nonnull PsiDirectory dir, @Nonnull String name, @Nonnull String templateName)
    throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiClass createClass(@Nonnull PsiDirectory dir,
                              @Nonnull String name,
                              @Nonnull String templateName,
                              boolean askForUndefinedVariables) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiClass createClass(@Nonnull PsiDirectory dir,
                              @Nonnull String name,
                              @Nonnull String templateName,
                              boolean askForUndefinedVariables, @Nonnull final Map<String, String> additionalProperties) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkCreateClass(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public PsiClass createInterface(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public PsiClass createEnum(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public PsiClass createAnnotationType(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSourceRoot(@Nonnull PsiDirectory dir) {
    return false;
  }

  @Override
  public LanguageLevel getLanguageLevel(@Nonnull PsiDirectory dir) {
    return LanguageLevel.HIGHEST;
  }
}
