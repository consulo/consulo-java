/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.navigation.ItemPresentation;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.util.IncorrectOperationException;
import consulo.language.Language;
import consulo.disposer.Disposable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;

/**
 * @author peter
 */
public class MockPsiDirectory extends MockPsiElement implements PsiDirectory {
  private final PsiJavaPackage myPackage;

  public MockPsiDirectory(final PsiJavaPackage aPackage, @Nonnull Disposable parentDisposable) {
    super(parentDisposable);
    myPackage = aPackage;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public void checkCreateFile(@Nonnull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkCreateFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkCreateSubdirectory(@Nonnull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkCreateSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiDirectory getParent() {
    return getParentDirectory();
  }


  @Override
  @Nonnull
  public PsiFile createFile(@Nonnull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method createFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nonnull
  public PsiFile copyFileFrom(@Nonnull final String newName, @Nonnull final PsiFile originalFile) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method copyFileFrom is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nonnull
  public PsiDirectory createSubdirectory(@Nonnull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method createSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiFile findFile(@Nonnull @NonNls final String name) {
    throw new UnsupportedOperationException("Method findFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiDirectory findSubdirectory(@Nonnull final String name) {
    throw new UnsupportedOperationException("Method findSubdirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nonnull
  public PsiFile[] getFiles() {
    throw new UnsupportedOperationException("Method getFiles is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nonnull
  public String getName() {
    throw new UnsupportedOperationException("Method getName is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiDirectory getParentDirectory() {
    final PsiJavaPackage psiPackage = myPackage.getParentPackage();
    return psiPackage == null ? null : new MockPsiDirectory(psiPackage, getProject());
  }

  @Override
  @Nonnull
  public PsiDirectory[] getSubdirectories() {
    throw new UnsupportedOperationException("Method getSubdirectories is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nonnull
  public VirtualFile getVirtualFile() {
    throw new UnsupportedOperationException("Method getVirtualFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    throw new UnsupportedOperationException("Method processChildren is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nonnull
  public PsiElement setName(@Nonnull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method setName is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkSetName(final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Method checkSetName is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public ItemPresentation getPresentation() {
    throw new UnsupportedOperationException("Method getPresentation is not yet implemented in " + getClass().getName());
  }
}
