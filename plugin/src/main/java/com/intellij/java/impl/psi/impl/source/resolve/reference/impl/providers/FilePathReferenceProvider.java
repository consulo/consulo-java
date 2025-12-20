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
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiLiteralExpression;
import consulo.module.Module;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.module.content.ModuleRootManager;
import consulo.util.lang.function.Condition;
import consulo.document.util.TextRange;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.*;
import consulo.language.psi.path.FileReference;
import consulo.language.psi.path.FileReferenceSet;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;
import java.util.*;

/**
 * @author cdr
 */
public class FilePathReferenceProvider extends PsiReferenceProvider {
  private final boolean myEndingSlashNotAllowed;

  public FilePathReferenceProvider() {
    this(true);
  }

  public FilePathReferenceProvider(boolean endingSlashNotAllowed) {
    myEndingSlashNotAllowed = endingSlashNotAllowed;
  }

  @Nonnull
  public PsiReference[] getReferencesByElement(PsiElement element, String text, int offset, boolean soft) {
    return getReferencesByElement(element, text, offset, soft, Module.EMPTY_ARRAY);
  }

  @Nonnull
  public PsiReference[] getReferencesByElement(PsiElement element, String text, int offset, final boolean soft,
                                               final @Nonnull Module... forModules) {
    return new FileReferenceSet(text, element, offset, this, true, myEndingSlashNotAllowed) {
      @Override
      protected boolean isSoft() {
        return soft;
      }

      @Override
      public boolean isAbsolutePathReference() {
        return true;
      }

      @Override
      public boolean couldBeConvertedTo(boolean relative) {
        return !relative;
      }

      @Override
      public boolean absoluteUrlNeedsStartSlash() {
        String s = getPathString();
        return s != null && s.length() > 0 && s.charAt(0) == '/';
      }

      @Override
      @Nonnull
      public Collection<PsiFileSystemItem> computeDefaultContexts() {
        Set<PsiFileSystemItem> systemItems = new HashSet<PsiFileSystemItem>();
        if (forModules.length > 0) {
          for (Module forModule : forModules) {
            systemItems.addAll(getRoots(forModule, true));
          }
        } else {
          systemItems.addAll(getRoots(ModuleUtil.findModuleForPsiElement(getElement()), true));
        }
        return systemItems;
      }

      @Override
      public FileReference createFileReference(TextRange range, int index, String text) {
        return FilePathReferenceProvider.this.createFileReference(this, range, index, text);
      }

      @Override
      public Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
        return new Condition<PsiFileSystemItem>() {
          @Override
          public boolean value(PsiFileSystemItem element) {
            return isPsiElementAccepted(element);
          }
        };
      }
    }.getAllReferences();
  }

  @Override
  public boolean acceptsTarget(@Nonnull PsiElement target) {
    return target instanceof PsiFileSystemItem;
  }

  protected boolean isPsiElementAccepted(PsiElement element) {
    return !(element instanceof PsiJavaFile && element instanceof PsiCompiledElement);
  }

  protected FileReference createFileReference(FileReferenceSet referenceSet, TextRange range, int index, String text) {
    return new FileReference(referenceSet, range, index, text);
  }

  @Override
  @Nonnull
  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
    String text = null;
    if (element instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression) element).getValue();
      if (value instanceof String) {
        text = (String) value;
      }
    }
    //else if (element instanceof XmlAttributeValue) {
    //  text = ((XmlAttributeValue)element).getValue();
    //}
    if (text == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    return getReferencesByElement(element, text, 1, true);
  }

  @Nonnull
  public static Collection<PsiFileSystemItem> getRoots(Module thisModule, boolean includingClasses) {
    if (thisModule == null) {
      return Collections.emptyList();
    }
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(thisModule);
    List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
    PsiManager psiManager = PsiManager.getInstance(thisModule.getProject());
    if (includingClasses) {
      VirtualFile[] libraryUrls = moduleRootManager.orderEntries().getAllLibrariesAndSdkClassesRoots();
      for (VirtualFile file : libraryUrls) {
        PsiDirectory directory = psiManager.findDirectory(file);
        if (directory != null) {
          result.add(directory);
        }
      }
    }

    VirtualFile[] sourceRoots = moduleRootManager.orderEntries().recursively().withoutSdk().withoutLibraries().getSourceRoots();
    for (VirtualFile root : sourceRoots) {
      PsiDirectory directory = psiManager.findDirectory(root);
      if (directory != null) {
        PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (aPackage != null && aPackage.getName() != null) {
          // package prefix
          result.add(PackagePrefixFileSystemItem.create(directory));
        } else {
          result.add(directory);
        }
      }
    }
    return result;
  }
}
