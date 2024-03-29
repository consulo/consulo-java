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
package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.openapi.vfs.ex.dummy.DummyFileSystem;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.PsiElementBase;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;

/**
 * @author Gregory.Shrago
*/
class PackagePrefixFileSystemItem extends PsiElementBase implements PsiFileSystemItem {
  private final PsiDirectory myDirectory;
  private final int myIndex;
  private final PsiJavaPackage[] myPackages;

  public static PackagePrefixFileSystemItem create(final PsiDirectory directory) {
    final ArrayList<PsiJavaPackage> packages = new ArrayList<PsiJavaPackage>();
    for (PsiJavaPackage cur = JavaDirectoryService.getInstance().getPackage(directory); cur != null; cur = cur.getParentPackage()) {
      packages.add(0, cur);
    }
    return new PackagePrefixFileSystemItem(directory, 0, packages.toArray(new PsiJavaPackage[packages.size()]));
  }

  private PackagePrefixFileSystemItem(final PsiDirectory directory, int index, final PsiJavaPackage[] packages) {
    myDirectory = directory;
    myIndex = index;
    myPackages = packages;
  }

  @Override
  @Nonnull
  public String getName() {
    return StringUtil.notNullize(myPackages[myIndex].getName());
  }

  @Override
  public PsiElement setName(@NonNls @Nonnull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkSetName(final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public PsiFileSystemItem getParent() {
    return myIndex > 0 ? new PackagePrefixFileSystemItem(myDirectory, myIndex - 1, myPackages) : myDirectory.getParent();
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return null;
  }

  @Override
  @Nonnull
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  public int getStartOffsetInParent() {
    return 0;
  }

  @Override
  public int getTextLength() {
    return 0;
  }

  @Override
  @Nullable
  public PsiElement findElementAt(final int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return 0;
  }

  @Override
  @NonNls
  public String getText() {
    return "";
  }

  @Override
  @Nonnull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY;
  }

  @Override
  public boolean textMatches(@Nonnull @NonNls final CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@Nonnull final PsiElement element) {
    return false;
  }

  @Override
  public void accept(@Nonnull final PsiElementVisitor visitor) {
  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  public PsiElement add(@Nonnull final PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addBefore(@Nonnull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addAfter(@Nonnull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkAdd(@Nonnull final PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement replace(@Nonnull final PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isValid() {
    return myDirectory.isValid();
  }

  @Override
  public boolean isWritable() {
    final VirtualFile file = getVirtualFile();
    return file != null && file.isWritable();
  }

  @Override
  public boolean isPhysical() {
    final VirtualFile file = getVirtualFile();
    return file != null && !(file.getFileSystem() instanceof DummyFileSystem);
  }

  @Override
  @Nullable
  public ASTNode getNode() {
    return null;
  }

  @Override
  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    if (myIndex == myPackages.length - 1) {
      return myDirectory.processChildren(processor);
    }
    else {
      return processor.execute(new PackagePrefixFileSystemItem(myDirectory, myIndex+1, myPackages));
    }
  }

  @Override
  @Nonnull
  public Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public PsiManager getManager() {
    return myDirectory.getManager();
  }

  @Override
  @Nonnull
  public PsiElement[] getChildren() {
    return myIndex == myPackages.length -1? myDirectory.getChildren() : new PsiElement[] {new PackagePrefixFileSystemItem(myDirectory, myIndex + 1, myPackages)};
  }

  @Override
  public boolean canNavigate() {
    return getVirtualFile() != null;
  }

  @Override
  public VirtualFile getVirtualFile() {
    if (myIndex == myPackages.length - 1) {
      return myDirectory.getVirtualFile();
    }
    else {
      return null;
    }
  }
}
